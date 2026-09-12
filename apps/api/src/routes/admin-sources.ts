import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requirePlatformAccess } from '../admin/access.js';

type SourceState = 'ok' | 'attention' | 'error' | 'unknown' | 'unmonitored';
type TimestampValue = Date | string | null;

type WeatherRow = {
  enabled_municipalities: number;
  cache_entries: number;
  expired_entries: number;
  last_success_at: TimestampValue;
  last_error_at: TimestampValue;
  last_error_code: string | null;
};

type RadarRow = {
  snapshots: number;
  failed_snapshots: number;
  analysis_ready_snapshots: number;
  last_observed_at: TimestampValue;
  last_fetched_at: TimestampValue;
  latest_status: 'fetched' | 'stored' | 'processed' | 'failed' | null;
  latest_analysis_ready: boolean | null;
  last_error_at: TimestampValue;
  last_error_code: string | null;
};

type OcrRow = {
  runs: number;
  runs_7d: number;
  succeeded_7d: number;
  failed_7d: number;
  queued_7d: number;
  processing_7d: number;
  latest_status: 'queued' | 'processing' | 'succeeded' | 'failed' | null;
  latest_provider: string | null;
  last_completed_at: TimestampValue;
  last_error_at: TimestampValue;
  last_error_code: string | null;
};

type LandReferenceRow = {
  source: 'catastro' | 'sigpac';
  references: number;
  verified_references: number;
  linked_references: number;
  last_checked_at: TimestampValue;
};

function milliseconds(value: TimestampValue) {
  if (!value) return null;
  const parsed = new Date(value).getTime();
  return Number.isNaN(parsed) ? null : parsed;
}

function weatherState(row: WeatherRow): SourceState {
  if (row.cache_entries === 0) return 'unknown';
  const lastSuccess = milliseconds(row.last_success_at);
  const lastError = milliseconds(row.last_error_at);
  if (lastError !== null && (lastSuccess === null || lastError > lastSuccess)) return 'error';
  if (row.expired_entries > 0) return 'attention';
  return 'ok';
}

function radarState(row: RadarRow): SourceState {
  if (row.snapshots === 0 || !row.latest_status) return 'unknown';
  if (row.latest_status === 'failed') return 'error';
  if (row.latest_status === 'fetched' || row.latest_status === 'stored') return 'attention';
  return 'ok';
}

function ocrState(row: OcrRow): SourceState {
  if (row.runs === 0 || !row.latest_status) return 'unknown';
  if (row.latest_status === 'failed') return 'error';
  if (row.latest_status === 'queued' || row.latest_status === 'processing') return 'attention';
  return 'ok';
}

function stateReason(source: 'weather' | 'radar' | 'ocr', state: SourceState) {
  if (source === 'weather') {
    if (state === 'unknown') return 'Todavía no hay previsiones AEMET almacenadas en la caché compartida.';
    if (state === 'error') return 'El último error registrado es posterior a la última descarga correcta.';
    if (state === 'attention') return 'Hay previsiones almacenadas cuya ventana de caché ya ha caducado.';
    return 'La caché compartida contiene previsiones vigentes y no registra un error posterior.';
  }
  if (source === 'radar') {
    if (state === 'unknown') return 'Todavía no hay snapshots de radar almacenados.';
    if (state === 'error') return 'El último snapshot terminó en estado failed.';
    if (state === 'attention') return 'El último snapshot fue capturado pero todavía no figura como procesado.';
    return 'El último snapshot almacenado figura como procesado. No se infiere disponibilidad continua del proveedor.';
  }
  if (state === 'unknown') return 'Todavía no hay ejecuciones OCR registradas.';
  if (state === 'error') return 'La ejecución OCR más reciente terminó con error.';
  if (state === 'attention') return 'La ejecución OCR más reciente sigue en cola o procesamiento.';
  return 'La ejecución OCR más reciente terminó correctamente.';
}

async function sourceTelemetry(database: DatabaseClient) {
  const [weatherResult, radarResult, ocrResult, landResult] = await Promise.all([
    sql<WeatherRow>`
      SELECT
        (SELECT count(*)::int FROM territory_municipalities WHERE active = true AND weather_enabled = true) AS enabled_municipalities,
        count(*)::int AS cache_entries,
        count(*) FILTER (WHERE expires_at <= now())::int AS expired_entries,
        max(fetched_at) AS last_success_at,
        max(last_error_at) AS last_error_at,
        (SELECT w2.last_error_code
           FROM weather_forecast_cache w2
          WHERE w2.last_error_at IS NOT NULL
          ORDER BY w2.last_error_at DESC
          LIMIT 1) AS last_error_code
      FROM weather_forecast_cache
    `.execute(database),
    sql<RadarRow>`
      SELECT
        count(*)::int AS snapshots,
        count(*) FILTER (WHERE status = 'failed')::int AS failed_snapshots,
        count(*) FILTER (WHERE analysis_ready = true)::int AS analysis_ready_snapshots,
        max(observed_at) AS last_observed_at,
        max(fetched_at) AS last_fetched_at,
        (SELECT r2.status FROM radar_snapshots r2 ORDER BY r2.fetched_at DESC LIMIT 1) AS latest_status,
        (SELECT r2.analysis_ready FROM radar_snapshots r2 ORDER BY r2.fetched_at DESC LIMIT 1) AS latest_analysis_ready,
        (SELECT r2.updated_at FROM radar_snapshots r2 WHERE r2.status = 'failed' ORDER BY r2.updated_at DESC LIMIT 1) AS last_error_at,
        (SELECT r2.error_code FROM radar_snapshots r2 WHERE r2.status = 'failed' ORDER BY r2.updated_at DESC LIMIT 1) AS last_error_code
      FROM radar_snapshots
    `.execute(database),
    sql<OcrRow>`
      SELECT
        count(*)::int AS runs,
        count(*) FILTER (WHERE created_at >= now() - interval '7 days')::int AS runs_7d,
        count(*) FILTER (WHERE status = 'succeeded' AND created_at >= now() - interval '7 days')::int AS succeeded_7d,
        count(*) FILTER (WHERE status = 'failed' AND created_at >= now() - interval '7 days')::int AS failed_7d,
        count(*) FILTER (WHERE status = 'queued' AND created_at >= now() - interval '7 days')::int AS queued_7d,
        count(*) FILTER (WHERE status = 'processing' AND created_at >= now() - interval '7 days')::int AS processing_7d,
        (SELECT o2.status FROM ocr_runs o2 ORDER BY o2.created_at DESC LIMIT 1) AS latest_status,
        (SELECT o2.provider FROM ocr_runs o2 ORDER BY o2.created_at DESC LIMIT 1) AS latest_provider,
        max(completed_at) AS last_completed_at,
        (SELECT o2.completed_at FROM ocr_runs o2 WHERE o2.status = 'failed' ORDER BY o2.created_at DESC LIMIT 1) AS last_error_at,
        (SELECT o2.error_code FROM ocr_runs o2 WHERE o2.status = 'failed' ORDER BY o2.created_at DESC LIMIT 1) AS last_error_code
      FROM ocr_runs
    `.execute(database),
    sql<LandReferenceRow>`
      SELECT source,
             count(*)::int AS references,
             count(*) FILTER (WHERE status = 'verified')::int AS verified_references,
             count(*) FILTER (WHERE status = 'linked')::int AS linked_references,
             max(checked_at) AS last_checked_at
        FROM field_land_refs
       WHERE source IN ('catastro', 'sigpac')
       GROUP BY source
    `.execute(database),
  ]);

  const weather = weatherResult.rows[0] ?? {
    enabled_municipalities: 0, cache_entries: 0, expired_entries: 0,
    last_success_at: null, last_error_at: null, last_error_code: null,
  };
  const radar = radarResult.rows[0] ?? {
    snapshots: 0, failed_snapshots: 0, analysis_ready_snapshots: 0,
    last_observed_at: null, last_fetched_at: null, latest_status: null,
    latest_analysis_ready: null, last_error_at: null, last_error_code: null,
  };
  const ocr = ocrResult.rows[0] ?? {
    runs: 0, runs_7d: 0, succeeded_7d: 0, failed_7d: 0, queued_7d: 0, processing_7d: 0,
    latest_status: null, latest_provider: null, last_completed_at: null, last_error_at: null, last_error_code: null,
  };
  const references = new Map(landResult.rows.map((row) => [row.source, row]));
  const emptyReference = (source: 'catastro' | 'sigpac'): LandReferenceRow => ({
    source, references: 0, verified_references: 0, linked_references: 0, last_checked_at: null,
  });
  const catastro = references.get('catastro') ?? emptyReference('catastro');
  const sigpac = references.get('sigpac') ?? emptyReference('sigpac');
  const weatherStatus = weatherState(weather);
  const radarStatus = radarState(radar);
  const ocrStatus = ocrState(ocr);

  return {
    generated_at: new Date().toISOString(),
    sources: [
      {
        id: 'aemet_forecast',
        name: 'AEMET · previsión municipal',
        provider: 'AEMET OpenData',
        telemetry: 'cache_health',
        state: weatherStatus,
        state_reason: stateReason('weather', weatherStatus),
        metrics: {
          enabled_municipalities: weather.enabled_municipalities,
          cache_entries: weather.cache_entries,
          expired_entries: weather.expired_entries,
        },
        timestamps: { last_success_at: weather.last_success_at, last_error_at: weather.last_error_at },
        last_error_code: weather.last_error_code,
      },
      {
        id: 'aemet_radar',
        name: 'AEMET · radar',
        provider: 'AEMET radar nacional',
        telemetry: 'pipeline_status',
        state: radarStatus,
        state_reason: stateReason('radar', radarStatus),
        metrics: {
          snapshots: radar.snapshots,
          failed_snapshots: radar.failed_snapshots,
          analysis_ready_snapshots: radar.analysis_ready_snapshots,
          latest_analysis_ready: radar.latest_analysis_ready ? 1 : 0,
        },
        timestamps: {
          last_observed_at: radar.last_observed_at,
          last_fetched_at: radar.last_fetched_at,
          last_error_at: radar.last_error_at,
        },
        last_error_code: radar.last_error_code,
      },
      {
        id: 'ocr',
        name: 'OCR de documentos',
        provider: ocr.latest_provider ?? 'Pipeline OCR',
        telemetry: 'pipeline_status',
        state: ocrStatus,
        state_reason: stateReason('ocr', ocrStatus),
        metrics: {
          runs: ocr.runs,
          runs_7d: ocr.runs_7d,
          succeeded_7d: ocr.succeeded_7d,
          failed_7d: ocr.failed_7d,
          queued_7d: ocr.queued_7d,
          processing_7d: ocr.processing_7d,
        },
        timestamps: { last_completed_at: ocr.last_completed_at, last_error_at: ocr.last_error_at },
        last_error_code: ocr.last_error_code,
      },
      {
        id: 'catastro',
        name: 'Catastro',
        provider: 'Dirección General del Catastro',
        telemetry: 'usage_only',
        state: 'unmonitored' as const,
        state_reason: 'Mágina conserva referencias y comprobaciones usadas, pero no persiste telemetría de disponibilidad del proveedor.',
        metrics: {
          references: catastro.references,
          verified_references: catastro.verified_references,
          linked_references: catastro.linked_references,
        },
        timestamps: { last_checked_at: catastro.last_checked_at },
        last_error_code: null,
      },
      {
        id: 'sigpac',
        name: 'SIGPAC',
        provider: 'SIGPAC',
        telemetry: 'usage_only',
        state: 'unmonitored' as const,
        state_reason: 'Mágina conserva referencias y comprobaciones usadas, pero no persiste telemetría de disponibilidad del proveedor.',
        metrics: {
          references: sigpac.references,
          verified_references: sigpac.verified_references,
          linked_references: sigpac.linked_references,
        },
        timestamps: { last_checked_at: sigpac.last_checked_at },
        last_error_code: null,
      },
    ],
  };
}

export function registerAdminSourceRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/sources', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    return sourceTelemetry(auth.database);
  });
}
