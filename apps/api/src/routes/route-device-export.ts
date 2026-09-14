import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { registerRouteAdventureRoutes } from './route-adventure.js';
import { registerRouteAdventureHubRoutes } from './route-adventure-hub.js';
import { registerRouteAdventureRewardRoutes } from './route-adventure-rewards.js';
import { registerRouteActivityRoutes } from './route-activity.js';
import { registerRouteActivityInsightRoutes } from './route-activity-insights.js';
import { registerRouteActivityAchievementRoutes } from './route-activity-achievements.js';
import { registerAdminRouteAdventureRoutes } from './admin-route-adventure.js';
import { registerAdminRouteAdventureBulkRoutes } from './admin-route-adventure-bulk.js';
import { registerAdminRouteAdventureReadinessRoutes } from './admin-route-adventure-readiness.js';
import { registerAdminRouteAdventureCandidateRoutes } from './admin-route-adventure-candidates.js';
import { registerAdminRouteAdventureProgressionRoutes } from './admin-route-adventure-progression.js';

const slugParams = z.object({ slug: z.string().regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/) });

function xml(value: string) {
  return value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&apos;');
}

type TrackPoint = { segment: number; point_order: number; latitude: number | string; longitude: number | string };

export function registerRouteDeviceExportRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  // Adventure and its explicit activity recorder belong to the same route feature bundle.
  // Keeping registration here avoids touching the shared app bootstrap while this branch stays isolated.
  registerRouteAdventureRoutes(app, db);
  registerRouteAdventureHubRoutes(app, db);
  registerRouteAdventureRewardRoutes(app, db);
  registerRouteActivityRoutes(app, db);
  registerRouteActivityInsightRoutes(app, db);
  registerRouteActivityAchievementRoutes(app, db);
  registerAdminRouteAdventureRoutes(app, db);
  registerAdminRouteAdventureBulkRoutes(app, db);
  registerAdminRouteAdventureReadinessRoutes(app, db);
  registerAdminRouteAdventureCandidateRoutes(app, db);
  registerAdminRouteAdventureProgressionRoutes(app, db);

  app.get('/api/v1/public/routes/:slug/gpx', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_unavailable' });
    const params = slugParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_slug' });

    const routeResult = await sql<{ id: string; name: string; slug: string }>`
      SELECT id, name, slug FROM routes
      WHERE slug = ${params.data.slug} AND status = 'published' AND track_status = 'validated'
      LIMIT 1
    `.execute(db);
    const route = routeResult.rows[0];
    if (!route) return reply.code(404).send({ error: 'route_not_found' });

    const pointsResult = await sql<TrackPoint>`
      SELECT
        CASE WHEN GeometryType(rt.geometry) = 'MULTILINESTRING' THEN (dp).path[1] ELSE 1 END::int AS segment,
        row_number() OVER (
          PARTITION BY CASE WHEN GeometryType(rt.geometry) = 'MULTILINESTRING' THEN (dp).path[1] ELSE 1 END
          ORDER BY (dp).path
        )::int AS point_order,
        ST_Y((dp).geom) AS latitude,
        ST_X((dp).geom) AS longitude
      FROM route_tracks rt
      CROSS JOIN LATERAL ST_DumpPoints(rt.geometry) dp
      WHERE rt.route_id = ${route.id}::uuid AND rt.validation_status = 'validated'
      ORDER BY segment, point_order
    `.execute(db);
    if (pointsResult.rows.length < 2) return reply.code(409).send({ error: 'validated_track_has_insufficient_points' });

    const segments = new Map<number, TrackPoint[]>();
    for (const point of pointsResult.rows) {
      const list = segments.get(point.segment) ?? [];
      list.push(point);
      segments.set(point.segment, list);
    }
    const trackSegments = [...segments.values()].map((points) =>
      `<trkseg>${points.map((point) => `<trkpt lat="${Number(point.latitude).toFixed(7)}" lon="${Number(point.longitude).toFixed(7)}"></trkpt>`).join('')}</trkseg>`
    ).join('');

    const gpx = `<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Mágina Olivo V20" xmlns="http://www.topografix.com/GPX/1/1"><metadata><name>${xml(route.name)}</name></metadata><trk><name>${xml(route.name)}</name>${trackSegments}</trk></gpx>`;
    const filename = `${route.slug}.gpx`;
    return reply
      .header('content-type', 'application/gpx+xml; charset=utf-8')
      .header('content-disposition', `attachment; filename="${filename}"`)
      .header('cache-control', 'public, max-age=300')
      .send(gpx);
  });
}
