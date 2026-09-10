import {
  analyzeRadarGrid,
  type RadarGeoTiffInspection,
  type RadarGrid,
  type RadarSpatialObservation,
} from '@magina/weather';
import type { Pool } from 'pg';

export const FARM_RADAR_ANALYSIS_VERSION = 'aemet-national-palette-v1-spatial-v1';
export const FARM_RADAR_RADIUS_KM = 80;
export const FARM_RADAR_MIN_DBZ = 12;

type FieldPointRow = {
  id: string;
  workspace_id: string;
  lat: number | string;
  lon: number | string;
};

export type FarmRadarProjectionOutcome = {
  eligibleFields: number;
  projected: number;
  detected: number;
  unavailable: number;
};

function dbCoverage(status: RadarSpatialObservation['coverageAtPoint']): 'covered' | 'outside' | 'unavailable' {
  if (status === 'covered') return 'covered';
  if (status === 'outside_raster') return 'outside';
  return 'unavailable';
}

function qualityFlags(
  observation: RadarSpatialObservation,
  inspection: RadarGeoTiffInspection,
): string[] {
  const flags = ['aemet_observed_reflectivity', 'palette_mapping_v1'];
  if (inspection.scaleSource) flags.push(inspection.scaleSource);
  if (observation.coverageAtPoint === 'no_coverage') flags.push('no_radar_coverage_at_field');
  if (observation.coverageAtPoint === 'unknown') flags.push('coverage_unknown');
  if (observation.coverageAtPoint === 'outside_raster') flags.push('outside_national_raster');
  return [...new Set(flags)];
}

export async function projectRadarSnapshotToFarms(input: {
  pool: Pool;
  snapshotId: string;
  observedAt: string | null;
  sourceName: string | null;
  inspection: RadarGeoTiffInspection;
  grid: RadarGrid;
}): Promise<FarmRadarProjectionOutcome> {
  if (!input.inspection.analysisReady) {
    return { eligibleFields: 0, projected: 0, detected: 0, unavailable: 0 };
  }
  if (!input.observedAt) {
    return { eligibleFields: 0, projected: 0, detected: 0, unavailable: 0 };
  }

  const fields = await input.pool.query<FieldPointRow>(`
    SELECT
      id,
      workspace_id,
      ST_Y(ST_PointOnSurface(geometry)) AS lat,
      ST_X(ST_PointOnSurface(geometry)) AS lon
    FROM fields
    WHERE status = 'active'
      AND geometry IS NOT NULL
  `);

  let projected = 0;
  let detected = 0;
  let unavailable = 0;

  for (const field of fields.rows) {
    const lat = Number(field.lat);
    const lon = Number(field.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;

    const observation = analyzeRadarGrid(
      input.grid,
      { lat, lon },
      { radiusKm: FARM_RADAR_RADIUS_KM, minDbz: FARM_RADAR_MIN_DBZ },
    );

    const pointHasEcho = observation.pointBand !== null;
    const precipitationDetected = observation.precipitationDetected;
    const nearestDistance = precipitationDetected
      ? (pointHasEcho ? 0 : observation.nearestEchoDistanceKm)
      : null;
    const directionDegrees = precipitationDetected && !pointHasEcho
      ? observation.nearestEchoBearingDeg
      : null;
    const directionLabel = precipitationDetected
      ? (pointHasEcho ? 'OVER_FIELD' : observation.nearestEchoDirection)
      : null;
    const nearestBand = pointHasEcho ? observation.pointBand : observation.nearestEchoBand;

    const metadata = {
      source_name: input.sourceName,
      scale_source: input.inspection.scaleSource,
      min_detection_dbz: FARM_RADAR_MIN_DBZ,
      search_radius_km: FARM_RADAR_RADIUS_KM,
      strongest_band_within_radius: observation.strongestBandWithinRadius,
      coverage_at_point: observation.coverageAtPoint,
      reason: observation.reason,
    };

    await input.pool.query(`
      INSERT INTO farm_radar_observations (
        workspace_id,
        field_id,
        radar_snapshot_id,
        analysis_version,
        observed_at,
        coverage_status,
        precipitation_detected,
        nearest_echo_distance_km,
        direction_degrees,
        direction_label,
        reflectivity_dbz_min,
        reflectivity_dbz_max,
        representative_dbz,
        analysis_radius_km,
        quality_flags,
        metadata_json
      ) VALUES (
        $1, $2, $3, $4, $5, $6, $7,
        $8, $9, $10, $11, $12, NULL, $13, $14::text[], $15::jsonb
      )
      ON CONFLICT (field_id, radar_snapshot_id, analysis_version)
      DO UPDATE SET
        observed_at = EXCLUDED.observed_at,
        coverage_status = EXCLUDED.coverage_status,
        precipitation_detected = EXCLUDED.precipitation_detected,
        nearest_echo_distance_km = EXCLUDED.nearest_echo_distance_km,
        direction_degrees = EXCLUDED.direction_degrees,
        direction_label = EXCLUDED.direction_label,
        reflectivity_dbz_min = EXCLUDED.reflectivity_dbz_min,
        reflectivity_dbz_max = EXCLUDED.reflectivity_dbz_max,
        representative_dbz = NULL,
        analysis_radius_km = EXCLUDED.analysis_radius_km,
        quality_flags = EXCLUDED.quality_flags,
        metadata_json = EXCLUDED.metadata_json,
        updated_at = now()
    `, [
      field.workspace_id,
      field.id,
      input.snapshotId,
      FARM_RADAR_ANALYSIS_VERSION,
      input.observedAt,
      dbCoverage(observation.coverageAtPoint),
      precipitationDetected,
      nearestDistance,
      directionDegrees,
      directionLabel,
      precipitationDetected ? nearestBand?.minDbz ?? null : null,
      precipitationDetected ? nearestBand?.maxDbz ?? null : null,
      FARM_RADAR_RADIUS_KM,
      qualityFlags(observation, input.inspection),
      JSON.stringify(metadata),
    ]);

    projected += 1;
    if (precipitationDetected) detected += 1;
    if (dbCoverage(observation.coverageAtPoint) === 'unavailable') unavailable += 1;
  }

  return {
    eligibleFields: fields.rows.length,
    projected,
    detected,
    unavailable,
  };
}
