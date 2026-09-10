import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

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
  snapshot_source: string | null;
  snapshot_product: string | null;
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

function finiteNumber(value: unknown): number | null {
  if (value == null) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function safeSummary(row: RadarRow) {
  if (!row.observation_id) return 'Todavía no hay una observación radar disponible para esta finca.';

  if (row.precipitation_detected === true) {
    if (row.direction_label === 'OVER_FIELD') {
      return 'Eco de precipitación detectado sobre la finca.';
    }
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

  if (row.coverage_status === 'outside') {
    return 'La finca está fuera del área cubierta por este producto radar.';
  }

  return 'Radar sin cobertura suficiente para esta finca.';
}

export function registerRadarRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/fields/:fieldId/radar/latest', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId) return reply.code(404).send({ error: 'field_not_found' });

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
        s.source AS snapshot_source,
        s.product AS snapshot_product
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
      });
    }

    return reply.send({
      field_id: row.field_id,
      observation: {
        id: row.observation_id,
        observed_at: row.observed_at,
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
      semantics: 'observed_reflectivity_not_forecast',
    });
  });
}
