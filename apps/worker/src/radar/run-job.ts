import { createHash } from 'node:crypto';
import {
  radarIngestJobPayloadSchema,
  radarSnapshotMetadataSchema,
  type RadarIngestJobPayload,
} from '@magina/contracts';
import {
  prepareAemetNationalRadarGrid,
  type RadarBinaryAsset,
  type RadarGeoTiffInspection,
  type RadarGrid,
} from '@magina/weather';
import type { Pool } from 'pg';
import type { RadarObjectStoragePort, RadarSourcePort } from './ports.js';
import {
  projectRadarSnapshotToFarms,
  type FarmRadarProjectionOutcome,
} from './project-farm-observations.js';

export type RadarIngestItemOutcome = {
  replayed: boolean;
  snapshotId: string;
  status: 'processed';
  analysisReady: boolean;
  sha256: string;
  storageKey: string;
  sourceName: string | null;
  validationErrors: string[];
  farmProjection: FarmRadarProjectionOutcome | null;
};

export type RadarIngestOutcome = {
  fetched: number;
  stored: number;
  replayed: number;
  analysisReady: number;
  projectedFarms: number;
  detectedFarms: number;
  snapshots: RadarIngestItemOutcome[];
};

function inspectionMetadata(job: RadarIngestJobPayload, asset: RadarBinaryAsset, inspection: RadarGeoTiffInspection) {
  return {
    requested_at: job.requested_at,
    source_name: asset.sourceName ?? null,
    geotiff_inspection: inspection,
  };
}

async function promoteStoredSnapshot(
  pool: Pool,
  snapshotId: string,
  job: RadarIngestJobPayload,
  asset: RadarBinaryAsset,
  inspection: RadarGeoTiffInspection,
) {
  await pool.query(`
    UPDATE radar_snapshots
    SET analysis_ready = $2,
        status = 'processed',
        metadata_json = metadata_json || $3::jsonb,
        error_code = NULL,
        updated_at = now()
    WHERE id = $1
  `, [snapshotId, inspection.analysisReady, JSON.stringify(inspectionMetadata(job, asset, inspection))]);
}

async function projectIfReady(input: {
  pool: Pool;
  snapshotId: string;
  asset: RadarBinaryAsset;
  inspection: RadarGeoTiffInspection;
  grid: RadarGrid | null;
}): Promise<FarmRadarProjectionOutcome | null> {
  if (!input.inspection.analysisReady || !input.grid) return null;
  return projectRadarSnapshotToFarms({
    pool: input.pool,
    snapshotId: input.snapshotId,
    observedAt: input.asset.metadata.observed_at,
    sourceName: input.asset.sourceName ?? null,
    inspection: input.inspection,
    grid: input.grid,
  });
}

async function ingestRadarAsset(
  pool: Pool,
  storage: RadarObjectStoragePort,
  job: RadarIngestJobPayload,
  asset: RadarBinaryAsset,
): Promise<RadarIngestItemOutcome> {
  const metadata = radarSnapshotMetadataSchema.parse(asset.metadata);

  if (metadata.source !== job.source || metadata.product !== job.product) {
    throw new Error('Radar source returned metadata that does not match the requested job');
  }
  if (metadata.asset_format !== 'geotiff') {
    throw new Error('Radar ingest only accepts GeoTIFF assets');
  }
  if (asset.bytes.byteLength === 0) throw new Error('Radar source returned an empty asset');

  const prepared = await prepareAemetNationalRadarGrid(asset.bytes);
  const inspection = prepared.inspection;
  const sha256 = createHash('sha256').update(asset.bytes).digest('hex');
  const previous = await pool.query<{
    id: string;
    status: 'fetched' | 'stored' | 'processed' | 'failed';
    storage_key: string | null;
    analysis_ready: boolean;
  }>(`
    SELECT id, status, storage_key, analysis_ready
    FROM radar_snapshots
    WHERE source = $1 AND product = $2 AND sha256 = $3
  `, [metadata.source, metadata.product, sha256]);

  const existing = previous.rows[0];
  if (existing?.storage_key && existing.status === 'processed') {
    const farmProjection = await projectIfReady({
      pool,
      snapshotId: existing.id,
      asset,
      inspection,
      grid: prepared.grid,
    });
    return {
      replayed: true,
      snapshotId: existing.id,
      status: 'processed',
      analysisReady: inspection.analysisReady,
      sha256,
      storageKey: existing.storage_key,
      sourceName: asset.sourceName ?? null,
      validationErrors: inspection.validationErrors,
      farmProjection,
    };
  }

  if (existing?.storage_key && existing.status === 'stored') {
    await promoteStoredSnapshot(pool, existing.id, job, asset, inspection);
    const farmProjection = await projectIfReady({
      pool,
      snapshotId: existing.id,
      asset,
      inspection,
      grid: prepared.grid,
    });
    return {
      replayed: true,
      snapshotId: existing.id,
      status: 'processed',
      analysisReady: inspection.analysisReady,
      sha256,
      storageKey: existing.storage_key,
      sourceName: asset.sourceName ?? null,
      validationErrors: inspection.validationErrors,
      farmProjection,
    };
  }

  const row = await pool.query<{ id: string }>(`
    INSERT INTO radar_snapshots (
      source, product, crs, observed_at, fetched_at, asset_format, analysis_ready,
      content_type, byte_size, sha256, source_url, status, metadata_json, error_code
    ) VALUES (
      $1, $2, $3, $4, $5, $6, $7,
      $8, $9, $10, $11, 'fetched', $12::jsonb, NULL
    )
    ON CONFLICT (source, product, sha256)
    DO UPDATE SET
      observed_at = COALESCE(radar_snapshots.observed_at, EXCLUDED.observed_at),
      fetched_at = GREATEST(radar_snapshots.fetched_at, EXCLUDED.fetched_at),
      content_type = EXCLUDED.content_type,
      byte_size = EXCLUDED.byte_size,
      source_url = EXCLUDED.source_url,
      analysis_ready = EXCLUDED.analysis_ready,
      metadata_json = EXCLUDED.metadata_json,
      error_code = NULL,
      updated_at = now()
    RETURNING id
  `, [
    metadata.source,
    metadata.product,
    metadata.crs,
    metadata.observed_at,
    metadata.fetched_at,
    metadata.asset_format,
    inspection.analysisReady,
    asset.contentType,
    asset.bytes.byteLength,
    sha256,
    asset.sourceUrl,
    JSON.stringify(inspectionMetadata(job, asset, inspection)),
  ]);

  const snapshotId = row.rows[0]?.id;
  if (!snapshotId) throw new Error('Radar snapshot insert did not return an id');

  const relativeKey = `${metadata.source}/${metadata.product}/${sha256}.tif`;

  try {
    const stored = await storage.putObject({
      key: relativeKey,
      bytes: asset.bytes,
      contentType: asset.contentType,
      sha256Hex: sha256,
    });

    await pool.query(`
      UPDATE radar_snapshots
      SET storage_key = $2,
          analysis_ready = $3,
          status = 'processed',
          error_code = NULL,
          updated_at = now()
      WHERE id = $1
    `, [snapshotId, stored.storageKey, inspection.analysisReady]);

    const farmProjection = await projectIfReady({
      pool,
      snapshotId,
      asset,
      inspection,
      grid: prepared.grid,
    });

    return {
      replayed: false,
      snapshotId,
      status: 'processed',
      analysisReady: inspection.analysisReady,
      sha256,
      storageKey: stored.storageKey,
      sourceName: asset.sourceName ?? null,
      validationErrors: inspection.validationErrors,
      farmProjection,
    };
  } catch (error) {
    await pool.query(`
      UPDATE radar_snapshots
      SET status = 'failed',
          analysis_ready = false,
          error_code = 'storage_or_projection_error',
          updated_at = now()
      WHERE id = $1
    `, [snapshotId]);
    throw error;
  }
}

export async function runRadarIngestJob(
  pool: Pool,
  source: RadarSourcePort,
  storage: RadarObjectStoragePort,
  rawJob: RadarIngestJobPayload,
): Promise<RadarIngestOutcome> {
  const job = radarIngestJobPayloadSchema.parse(rawJob);
  const assets = await source.fetchNationalReflectivity();
  if (assets.length === 0) throw new Error('Radar source returned no GeoTIFF assets');

  const snapshots: RadarIngestItemOutcome[] = [];
  for (const asset of assets) {
    snapshots.push(await ingestRadarAsset(pool, storage, job, asset));
  }

  return {
    fetched: snapshots.length,
    stored: snapshots.filter((item) => !item.replayed).length,
    replayed: snapshots.filter((item) => item.replayed).length,
    analysisReady: snapshots.filter((item) => item.analysisReady).length,
    projectedFarms: snapshots.reduce((sum, item) => sum + (item.farmProjection?.projected ?? 0), 0),
    detectedFarms: snapshots.reduce((sum, item) => sum + (item.farmProjection?.detected ?? 0), 0),
    snapshots,
  };
}
