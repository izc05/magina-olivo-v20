import { prepareAemetNationalRadarGrid } from '@magina/weather';
import { radarAlertRuleInputSchema } from '@magina/contracts';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';
import type { StoragePort } from '../storage/port.js';
import { renderRadarOverlayPng } from '../weather/radar-overlay.js';

type RadarRow = {
  field_id: string;
  observation_id: string | null;
  observed_at: Date | string | null;
  coverage_status: 'covered' | 'partial' | 'outside' | 'unavailable' | null;
  precipitation_detected: boolean | null;
  nearest_echo_distance_km: number | string | null;
  direction_degrees: number | string | null;
  direction_label: 'N' | 'NE' | 'E' | 'SE' | 'S' | 'SW' | 'W' | 'NW' | 'OVER_FIELD' | null;
  reflectivity_dbz_min: number | string | null;
  reflectivity_dbz_max: number | string | null;
  analysis_radius_km: number | string | null;
  quality_flags: string[] | null;
  analysis_version: string | null;
  snapshot_id: string | null;
  snapshot_source: string | null;
  snapshot_product: string | null;
  snapshot_fetched_at: Date | string | null;
  snapshot_storage_key: string | null;
  snapshot_analysis_ready: boolean | null;
  snapshot_status: string | null;
  snapshot_metadata: unknown;
};

type RadarAlertRuleRow = {
  id: string;
  user_id: string;
  workspace_id: string;
  field_id: string;
  enabled: boolean;
  radius_km: number | string;
  min_dbz: number | string;
  cooldown_minutes: number;
  created_at: Date | string;
  updated_at: Date | string;
};

type RadarOverlayRow = {
  field_id: string;
  snapshot_id: string | null;
  observed_at: Date | string | null;
  storage_key: string | null;
  analysis_ready: boolean | null;
  snapshot_status: string | null;
};

const DIRECTION_ES: Record<Exclude<NonNullable<RadarRow['direction_label']>, 'OVER_FIELD'>, string> = {
  N: 'norte',
  NE: 'noreste',
  E: 'este',
  SE: 'sureste',
  S: 'sur',
  SW: 'suroeste',
  W: 'oeste',
  NW: 'noroeste',
};

const RADAR_ALERT_DEFAULTS = {
  enabled: true,
  radius_km: 10,
  min_dbz: 12,
  cooldown_minutes: 60,
} as const;

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_RADAR_ASSET_BYTES = 50 * 1024 * 1024;

function finiteNumber(value: unknown): number | null {
  if (value == null) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function staleAfterMinutes() {
  const configured = Number(process.env.RADAR_STALE_AFTER_MINUTES ?? 30);
  return Number.isFinite(configured) && configured >= 10 ? configured : 30;
}

function timestampAgeSeconds(value: Date | string | null) {
  if (!value) return null;
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return null;
  return Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
}

function radarFreshness(value: Date | string | null) {
  const ageSeconds = timestampAgeSeconds(value);
  const thresholdMinutes = staleAfterMinutes();
  if (ageSeconds == null) {
    return { status: 'unknown' as const, age_seconds: null, stale_after_minutes: thresholdMinutes };
  }
  return {
    status: ageSeconds > thresholdMinutes * 60 ? 'stale' as const : 'fresh' as const,
    age_seconds: ageSeconds,
    stale_after_minutes: thresholdMinutes,
  };
}

function metadataBbox(metadata: unknown): [number, number, number, number] | null {
  if (!metadata || typeof metadata !== 'object') return null;
  const inspection = (metadata as { geotiff_inspection?: unknown }).geotiff_inspection;
  if (!inspection || typeof inspection !== 'object') return null;
  const bbox = (inspection as { bbox?: unknown }).bbox;
  if (!Array.isArray(bbox) || bbox.length < 4) return null;
  const values = bbox.slice(0, 4).map(Number);
  if (!values.every(Number.isFinite)) return null;
  if (values[0]! >= values[2]! || values[1]! >= values[3]!) return null;
  return values as [number, number, number, number];
}

function serializeAlertRule(row: RadarAlertRuleRow) {
  return {
    id: row.id,
    user_id: row.user_id,
    workspace_id: row.workspace_id,
    field_id: row.field_id,
    enabled: row.enabled,
    radius_km: finiteNumber(row.radius_km),
    min_dbz: finiteNumber(row.min_dbz),
    cooldown_minutes: row.cooldown_minutes,
    created_at: row.created_at,
    updated_at: row.updated_at,
  };
}

function safeSummary(row: RadarRow) {
  if (!row.observation_id) return 'Todavía no hay una observación radar disponible para esta finca.';

  if (row.precipitation_detected === true) {
    if (row.direction_label === 'OVER_FIELD') return 'Eco de precipitación detectado sobre la finca.';
    const distance = finiteNumber(row.nearest_echo_distance_km);
    const direction = row.direction_label ? DIRECTION_ES[row.direction_label] : null;
    if (distance !== null && direction) {
      const rounded = distance < 10 ? Math.round(distance * 10) / 10 : Math.round(distance);
      return `Precipitación detectada a unos ${rounded} km al ${direction}.`;
    }
    return 'Precipitación detectada cerca de la finca.';
  }

  if (row.coverage_status === 'covered' && row.precipitation_detected === false) {
    const radius = finiteNumber(row.analysis_radius_km);
    return radius !== null
      ? `Sin ecos de precipitación detectados en ${Math.round(radius)} km.`
      : 'Sin ecos de precipitación detectados en el área analizada.';
  }
  if (row.coverage_status === 'outside') return 'La finca está fuera del área cubierta por este producto radar.';
  return 'Radar sin cobertura suficiente para esta finca.';
}

export function registerRadarRoutes(app: FastifyInstance, db: DatabaseClient | null, storage: StoragePort) {
  const overlayCache = new Map<string, Buffer>();

  app.get('/api/v1/fields/:fieldId/radar/latest', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !uuidPattern.test(fieldId)) return reply.code(404).send({ error: 'field_not_found' });

    const result = await sql<RadarRow>`
      SELECT
        f.id AS field_id,
        o.id AS observation_id,
        o.observed_at,
        o.coverage_status,
        o.precipitation_detected,
        o.nearest_echo_distance_km,
        o.direction_degrees,
        o.direction_label,
        o.reflectivity_dbz_min,
        o.reflectivity_dbz_max,
        o.analysis_radius_km,
        o.quality_flags,
        o.analysis_version,
        s.id AS snapshot_id,
        s.source AS snapshot_source,
        s.product AS snapshot_product,
        s.fetched_at AS snapshot_fetched_at,
        s.storage_key AS snapshot_storage_key,
        s.analysis_ready AS snapshot_analysis_ready,
        s.status AS snapshot_status,
        s.metadata_json AS snapshot_metadata
      FROM fields f
      LEFT JOIN LATERAL (
        SELECT *
        FROM farm_radar_observations fro
        WHERE fro.field_id = f.id
          AND fro.workspace_id = f.workspace_id
        ORDER BY fro.observed_at DESC, fro.created_at DESC
        LIMIT 1
      ) o ON true
      LEFT JOIN radar_snapshots s ON s.id = o.radar_snapshot_id
      WHERE f.id = ${fieldId}::uuid
        AND f.workspace_id = ${context.workspaceId}::uuid
        AND f.status = 'active'
      LIMIT 1
    `.execute(database);

    const row = result.rows[0];
    if (!row) return reply.code(404).send({ error: 'field_not_found' });

    if (!row.observation_id) {
      return reply.send({
        field_id: row.field_id,
        observation: null,
        summary: safeSummary(row),
        attribution: 'AEMET',
        freshness: { status: 'unavailable', age_seconds: null, stale_after_minutes: staleAfterMinutes() },
        overlay: { status: 'unavailable', bbox: null, url: null },
        semantics: 'observed_reflectivity_not_forecast',
      });
    }

    const bbox = metadataBbox(row.snapshot_metadata);
    const overlayReady = Boolean(
      row.snapshot_id
      && row.snapshot_analysis_ready
      && row.snapshot_status === 'processed'
      && row.snapshot_storage_key
      && bbox,
    );

    return reply.send({
      field_id: row.field_id,
      observation: {
        id: row.observation_id,
        observed_at: row.observed_at,
        fetched_at: row.snapshot_fetched_at,
        coverage_status: row.coverage_status,
        precipitation_detected: row.precipitation_detected,
        nearest_echo: row.precipitation_detected
          ? {
              distance_km: finiteNumber(row.nearest_echo_distance_km),
              direction_degrees: finiteNumber(row.direction_degrees),
              direction: row.direction_label,
              reflectivity_dbz: {
                min: finiteNumber(row.reflectivity_dbz_min),
                max: finiteNumber(row.reflectivity_dbz_max),
              },
            }
          : null,
        analysis_radius_km: finiteNumber(row.analysis_radius_km),
        quality_flags: row.quality_flags ?? [],
        analysis_version: row.analysis_version,
        source: row.snapshot_source,
        product: row.snapshot_product,
      },
      summary: safeSummary(row),
      attribution: 'AEMET',
      freshness: radarFreshness(row.observed_at),
      overlay: overlayReady
        ? { status: 'ready', bbox, url: `/api/v1/fields/${fieldId}/radar/latest/overlay.png` }
        : { status: 'unavailable', bbox: null, url: null },
      semantics: 'observed_reflectivity_not_forecast',
    });
  });

  app.get('/api/v1/fields/:fieldId/radar/latest/overlay.png', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !uuidPattern.test(fieldId)) return reply.code(404).send({ error: 'field_not_found' });

    const result = await sql<RadarOverlayRow>`
      SELECT f.id AS field_id, s.id AS snapshot_id, o.observed_at, s.storage_key,
             s.analysis_ready, s.status AS snapshot_status
      FROM fields f
      LEFT JOIN LATERAL (
        SELECT * FROM farm_radar_observations fro
        WHERE fro.field_id = f.id AND fro.workspace_id = f.workspace_id
        ORDER BY fro.observed_at DESC, fro.created_at DESC LIMIT 1
      ) o ON true
      LEFT JOIN radar_snapshots s ON s.id = o.radar_snapshot_id
      WHERE f.id = ${fieldId}::uuid
        AND f.workspace_id = ${context.workspaceId}::uuid
        AND f.status = 'active'
      LIMIT 1
    `.execute(database);

    const row = result.rows[0];
    if (!row) return reply.code(404).send({ error: 'field_not_found' });
    if (!row.snapshot_id || !row.storage_key || !row.analysis_ready || row.snapshot_status !== 'processed') {
      return reply.code(404).send({ error: 'radar_overlay_not_available' });
    }

    try {
      let png = overlayCache.get(row.snapshot_id);
      if (!png) {
        const readUrl = await storage.createReadUrl(row.storage_key, 120);
        const upstream = await fetch(readUrl, { signal: AbortSignal.timeout(15_000) });
        if (!upstream.ok) throw new Error(`radar_storage_${upstream.status}`);
        const declaredSize = Number(upstream.headers.get('content-length'));
        if (Number.isFinite(declaredSize) && declaredSize > MAX_RADAR_ASSET_BYTES) throw new Error('radar_asset_too_large');
        const bytes = new Uint8Array(await upstream.arrayBuffer());
        if (bytes.byteLength === 0 || bytes.byteLength > MAX_RADAR_ASSET_BYTES) throw new Error('invalid_radar_asset_size');
        const prepared = await prepareAemetNationalRadarGrid(bytes);
        if (!prepared.inspection.analysisReady || !prepared.grid || !prepared.inspection.bbox) {
          return reply.code(422).send({ error: 'radar_overlay_validation_failed' });
        }
        png = renderRadarOverlayPng(prepared.grid);
        overlayCache.set(row.snapshot_id, png);
        while (overlayCache.size > 3) {
          const oldest = overlayCache.keys().next().value as string | undefined;
          if (!oldest) break;
          overlayCache.delete(oldest);
        }
      }

      reply.header('content-type', 'image/png');
      reply.header('content-length', png.byteLength);
      reply.header('x-radar-semantics', 'observed-reflectivity-not-forecast');
      if (row.observed_at) reply.header('x-radar-observed-at', new Date(row.observed_at).toISOString());
      return reply.send(png);
    } catch (error) {
      request.log.warn({ error, fieldId }, 'radar overlay unavailable');
      return reply.code(503).send({ error: 'radar_overlay_upstream_unavailable' });
    }
  });

  app.get('/api/v1/fields/:fieldId/radar/alert-rule', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !uuidPattern.test(fieldId)) return reply.code(404).send({ error: 'field_not_found' });
    const field = await fieldBelongsToWorkspace(database, fieldId, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const result = await sql<RadarAlertRuleRow>`
      SELECT id, user_id, workspace_id, field_id, enabled, radius_km, min_dbz,
             cooldown_minutes, created_at, updated_at
      FROM radar_alert_rules
      WHERE user_id = ${context.userId}::uuid
        AND field_id = ${fieldId}::uuid
        AND workspace_id = ${context.workspaceId}::uuid
      LIMIT 1
    `.execute(database);

    const rule = result.rows[0];
    return reply.send({
      field_id: fieldId,
      rule: rule ? serializeAlertRule(rule) : null,
      defaults: RADAR_ALERT_DEFAULTS,
      semantics: 'observed_reflectivity_only',
    });
  });

  app.put('/api/v1/fields/:fieldId/radar/alert-rule', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !uuidPattern.test(fieldId)) return reply.code(404).send({ error: 'field_not_found' });
    const field = await fieldBelongsToWorkspace(database, fieldId, context.workspaceId);
    if (!field) return reply.code(404).send({ error: 'field_not_found' });

    const input = parseBody(radarAlertRuleInputSchema, request.body, reply);
    if (!input) return;

    const result = await sql<RadarAlertRuleRow>`
      INSERT INTO radar_alert_rules (
        user_id, workspace_id, field_id, enabled, radius_km, min_dbz, cooldown_minutes
      ) VALUES (
        ${context.userId}::uuid,
        ${context.workspaceId}::uuid,
        ${fieldId}::uuid,
        ${input.enabled},
        ${input.radius_km},
        ${input.min_dbz},
        ${input.cooldown_minutes}
      )
      ON CONFLICT (user_id, field_id)
      DO UPDATE SET
        workspace_id = EXCLUDED.workspace_id,
        enabled = EXCLUDED.enabled,
        radius_km = EXCLUDED.radius_km,
        min_dbz = EXCLUDED.min_dbz,
        cooldown_minutes = EXCLUDED.cooldown_minutes,
        updated_at = now()
      RETURNING id, user_id, workspace_id, field_id, enabled, radius_km, min_dbz,
                cooldown_minutes, created_at, updated_at
    `.execute(database);

    const rule = result.rows[0];
    if (!rule) return reply.code(500).send({ error: 'radar_alert_rule_not_saved' });
    return reply.send({
      field_id: fieldId,
      rule: serializeAlertRule(rule),
      semantics: 'observed_reflectivity_only',
    });
  });
}
