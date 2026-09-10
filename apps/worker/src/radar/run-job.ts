import { createHash } from 'node:crypto';
import {
  radarIngestJobPayloadSchema,
  radarSnapshotMetadataSchema,
  type RadarIngestJobPayload,
} from '@magina/contracts';
import type { RadarBinaryAsset } from '@magina/weather';
import type { Pool } from 'pg';
import type { RadarObjectStoragePort, RadarSourcePort } from './ports.js';

export type RadarIngestItemOutcome = {
  replayed: boolean;
  snapshotId: string;
  status: 'stored' | 'processed';
  analysisReady: true;
  sha256: string;
  storageKey: string;
  sourceName: string | null;
};

export type RadarIngestOutcome = {
  fetched: number;
  stored: number;
  replayed: number;
  snapshots: RadarIngestItemOutcome[];
};

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
  if (metadata.asset_format !== 'geotiff' || metadata.analysis_ready !== true) {
    throw new Error('Radar ingest only accepts analytical GeoTIFF assets');
  }
  if (asset.bytes.byteLength === 0) throw new Error('Radar source returned an empty asset');

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
  if (existing?.storage_key && (existing.status === 'stored' || existing.status === 'processed')) {
    return {
      replayed: true,
      snapshotId: existing.id,
      status: existing.status,
      analysisReady: true,
      sha256,
      storageKey: existing.storage_key,
      sourceName: asset.sourceName ?? null,
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
    metadata.analysis_ready,
    asset.contentType,
    asset.bytes.byteLength,
    sha256,
    asset.sourceUrl,
    JSON.stringify({
      requested_at: job.requested_at,
      source_name: asset.sourceName ?? null,
    }),
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
          status = 'stored',
          error_code = NULL,
          updated_at = now()
      WHERE id = $1
    `, [snapshotId, stored.storageKey]);

    return {
      replayed: false,
      snapshotId,
      status: 'stored',
      analysisReady: true,
      sha256,
      storageKey: stored.storageKey,
      sourceName: asset.sourceName ?? null,
    };
  } catch (error) {
    await pool.query(`
      UPDATE radar_snapshots
      SET status = 'failed',
          error_code = 'storage_error',
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
    snapshots,
  };
}
