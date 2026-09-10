import { z } from 'zod';

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

export type RadarSnapshotMetadata = z.infer<typeof radarSnapshotMetadataSchema>;
