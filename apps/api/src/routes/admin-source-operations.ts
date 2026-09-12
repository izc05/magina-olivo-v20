import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import type { NotificationDispatchQueuePort } from '../notifications/port.js';
import type { RadarIngestQueuePort } from '../radar/port.js';
import { getCachedMunicipalityForecast } from '../weather/cache.js';
import type { MunicipalityWeatherProvider } from '../weather/providers.js';

const WEATHER_REFRESH_LIMIT = 50;
const NOTIFICATION_DISPATCH_LIMIT = 50;

type WeatherTarget = {
  id: string;
  aemet_code: string;
};

type WeatherRefreshResult = {
  processed: number;
  fresh: number;
  refreshed: number;
  stale: number;
  failed: number;
  limit: number;
};

function safeErrorCode(error: unknown, fallback: string) {
  return error instanceof Error && error.message
    ? error.message.slice(0, 160)
    : fallback;
}

async function refreshWeatherSources(
  database: DatabaseClient,
  provider: MunicipalityWeatherProvider,
): Promise<WeatherRefreshResult> {
  const targets = await sql<WeatherTarget>`
    SELECT id, aemet_code
    FROM territory_municipalities
    WHERE active = true
      AND weather_enabled = true
      AND aemet_code IS NOT NULL
    ORDER BY name ASC
    LIMIT ${WEATHER_REFRESH_LIMIT}
  `.execute(database);

  const result: WeatherRefreshResult = {
    processed: targets.rows.length,
    fresh: 0,
    refreshed: 0,
    stale: 0,
    failed: 0,
    limit: WEATHER_REFRESH_LIMIT,
  };

  for (const target of targets.rows) {
    try {
      const forecast = await getCachedMunicipalityForecast(
        database,
        provider,
        target.id,
        target.aemet_code,
      );
      result[forecast.cacheStatus] += 1;
    } catch {
      result.failed += 1;
    }
  }

  return result;
}

export function registerAdminSourceOperationRoutes(
  app: FastifyInstance,
  db: DatabaseClient | null,
  weatherProvider: MunicipalityWeatherProvider,
  radarQueue: RadarIngestQueuePort,
  notificationQueue: NotificationDispatchQueuePort,
) {
  app.post('/api/v1/admin/sources/aemet_forecast/refresh', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;

    const result = await refreshWeatherSources(auth.database, weatherProvider);
    const completedAt = new Date().toISOString();
    await auditAdminAction(
      auth.database,
      auth.access,
      'source.weather_refresh',
      'data_source',
      'aemet_forecast',
      { ...result, completed_at: completedAt },
    );

    return reply.send({ source: 'aemet_forecast', ...result, completed_at: completedAt });
  });

  app.post('/api/v1/admin/sources/aemet_radar/ingest', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;

    const queuedAt = new Date().toISOString();
    try {
      const jobId = await radarQueue.enqueue({
        version: 1,
        source: 'aemet_national_mosaic',
        product: 'reflectivity',
        requested_at: queuedAt,
      });
      await auditAdminAction(
        auth.database,
        auth.access,
        'source.radar_ingest_queued',
        'data_source',
        'aemet_radar',
        { job_id: jobId, queued_at: queuedAt },
      );
      return reply.code(202).send({ source: 'aemet_radar', job_id: jobId, queued_at: queuedAt });
    } catch (error) {
      const errorCode = safeErrorCode(error, 'radar_ingest_queue_unavailable');
      request.log.warn({ error_code: errorCode }, 'Unable to queue admin radar ingest');
      await auditAdminAction(
        auth.database,
        auth.access,
        'source.radar_ingest_queue_failed',
        'data_source',
        'aemet_radar',
        { error_code: errorCode },
      );
      return reply.code(503).send({ error: 'radar_ingest_queue_unavailable' });
    }
  });

  app.post('/api/v1/admin/sources/notifications/dispatch', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;

    const queuedAt = new Date().toISOString();
    try {
      const jobId = await notificationQueue.enqueue({ version: 1, limit: NOTIFICATION_DISPATCH_LIMIT });
      await auditAdminAction(
        auth.database,
        auth.access,
        'source.notification_dispatch_queued',
        'data_source',
        'notifications',
        { job_id: jobId, queued_at: queuedAt, limit: NOTIFICATION_DISPATCH_LIMIT },
      );
      return reply.code(202).send({
        source: 'notifications',
        job_id: jobId,
        queued_at: queuedAt,
        limit: NOTIFICATION_DISPATCH_LIMIT,
      });
    } catch (error) {
      const errorCode = safeErrorCode(error, 'notification_dispatch_queue_unavailable');
      request.log.warn({ error_code: errorCode }, 'Unable to queue admin notification dispatch');
      await auditAdminAction(
        auth.database,
        auth.access,
        'source.notification_dispatch_queue_failed',
        'data_source',
        'notifications',
        { error_code: errorCode },
      );
      return reply.code(503).send({ error: 'notification_dispatch_queue_unavailable' });
    }
  });
}
