import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requirePlatformAccess } from '../admin/access.js';

type CandidateRow = {
  route_id: string;
  slug: string;
  route_name: string;
  route_status: string;
  track_status: string;
  distance_m: number | null;
  difficulty: string | null;
  municipality_name: string | null;
  validated_track_count: number;
  active_poi_count: number;
  checkpoint_count: number;
  required_checkpoint_count: number;
  adventure_enabled: boolean;
  adventure_title: string | null;
  candidate_score: number;
};

export function registerAdminRouteAdventureCandidateRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/adventures/candidates', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;

    const result = await sql<CandidateRow>`
      SELECT
        r.id AS route_id,
        r.slug,
        r.name AS route_name,
        r.status AS route_status,
        r.track_status,
        r.distance_m,
        r.difficulty,
        tm.name AS municipality_name,
        (SELECT COUNT(*)::int FROM route_tracks rt
          WHERE rt.route_id = r.id AND rt.validation_status = 'validated') AS validated_track_count,
        (SELECT COUNT(*)::int FROM route_points rp
          WHERE rp.route_id = r.id AND rp.active = true AND rp.location IS NOT NULL) AS active_poi_count,
        (SELECT COUNT(*)::int FROM route_adventure_checkpoints cp
          WHERE cp.route_id = r.id AND cp.active = true) AS checkpoint_count,
        (SELECT COUNT(*)::int FROM route_adventure_checkpoints cp
          WHERE cp.route_id = r.id AND cp.active = true AND cp.is_required = true) AS required_checkpoint_count,
        COALESCE(ra.enabled, false) AS adventure_enabled,
        ra.title AS adventure_title,
        (
          CASE WHEN r.status = 'published' THEN 35 ELSE 0 END
          + CASE WHEN r.track_status = 'validated' AND EXISTS (
              SELECT 1 FROM route_tracks rt
              WHERE rt.route_id = r.id AND rt.validation_status = 'validated'
            ) THEN 35 ELSE 0 END
          + LEAST(25, (SELECT COUNT(*)::int FROM route_points rp
              WHERE rp.route_id = r.id AND rp.active = true AND rp.location IS NOT NULL) * 5)
          + CASE WHEN ra.route_id IS NOT NULL THEN 5 ELSE 0 END
        )::int AS candidate_score
      FROM routes r
      LEFT JOIN territory_municipalities tm ON tm.id = r.municipality_id
      LEFT JOIN route_adventures ra ON ra.route_id = r.id
      WHERE r.status <> 'archived'
      ORDER BY candidate_score DESC, active_poi_count DESC, r.name
    `.execute(auth.database);

    return {
      candidates: result.rows.map((row) => {
        const validatedTrackCount = Number(row.validated_track_count);
        const activePoiCount = Number(row.active_poi_count);
        const checkpointCount = Number(row.checkpoint_count);
        const requiredCheckpointCount = Number(row.required_checkpoint_count);
        const canSeed = validatedTrackCount > 0 && activePoiCount > 0;
        const readyToPublish = row.route_status === 'published'
          && row.track_status === 'validated'
          && validatedTrackCount > 0
          && checkpointCount > 0
          && requiredCheckpointCount > 0;

        const blockers: string[] = [];
        if (row.route_status !== 'published') blockers.push('route_not_published');
        if (row.track_status !== 'validated' || validatedTrackCount < 1) blockers.push('validated_track_missing');
        if (activePoiCount < 1 && checkpointCount < 1) blockers.push('route_poi_missing');
        if (checkpointCount < 1) blockers.push('adventure_checkpoint_missing');
        if (checkpointCount > 0 && requiredCheckpointCount < 1) blockers.push('required_checkpoint_missing');

        return {
          ...row,
          validated_track_count: validatedTrackCount,
          active_poi_count: activePoiCount,
          checkpoint_count: checkpointCount,
          required_checkpoint_count: requiredCheckpointCount,
          candidate_score: Number(row.candidate_score),
          can_seed_from_poi: canSeed,
          ready_to_publish: readyToPublish,
          blockers,
        };
      }),
    };
  });
}
