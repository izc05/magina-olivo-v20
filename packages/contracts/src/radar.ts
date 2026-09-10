import { z } from 'zod';
import { uuidSchema } from './mi-campo.js';

export const radarSourceSchema = z.enum(['aemet_national_mosaic']);
export const radarProductSchema = z.enum(['reflectivity']);
export const radarAssetFormatSchema = z.enum(['gif', 'png', 'jpeg', 'geotiff', 'unknown']);

export const radarSnapshotMetadataSchema = z.object({
  source: radarSourceSchema,
  product: radarProductSchema,
  crs: z.literal('EPSG:4326'),
  observed_at: z.string().datetime({ offset: true }).nullable(),
  fetched_at: z.string().datetime({ offset: true }),
  asset_format: radarAssetFormatSchema,
  analysis_ready: z.boolean(),
});

export const radarCoverageStatusSchema = z.enum(['covered', 'partial', 'outside', 'unavailable']);
export const radarDirectionLabelSchema = z.enum(['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW', 'OVER_FIELD']);

export const farmRadarObservationSchema = z.object({
  id: uuidSchema,
  workspace_id: uuidSchema,
  field_id: uuidSchema,
  radar_snapshot_id: uuidSchema,
  analysis_version: z.string().min(1).max(80),
  observed_at: z.string().datetime({ offset: true }),
  coverage_status: radarCoverageStatusSchema,
  precipitation_detected: z.boolean().nullable(),
  nearest_echo_distance_km: z.number().nonnegative().nullable(),
  direction_degrees: z.number().min(0).lt(360).nullable(),
  direction_label: radarDirectionLabelSchema.nullable(),
  reflectivity_dbz_min: z.number().nullable(),
  reflectivity_dbz_max: z.number().nullable(),
  representative_dbz: z.number().nullable(),
  analysis_radius_km: z.number().positive().max(100),
  quality_flags: z.array(z.string().min(1).max(120)).max(32),
  created_at: z.string().datetime({ offset: true }),
});

export type RadarSnapshotMetadata = z.infer<typeof radarSnapshotMetadataSchema>;
export type RadarCoverageStatus = z.infer<typeof radarCoverageStatusSchema>;
export type RadarDirectionLabel = z.infer<typeof radarDirectionLabelSchema>;
export type FarmRadarObservation = z.infer<typeof farmRadarObservationSchema>;
