import { createHash } from 'node:crypto';
import {
  radarIngestJobPayloadSchema,
  radarSnapshotMetadataSchema,
  type RadarIngestJobPayload,
} from '@magina/contracts';
import type { Pool } from 'pg';
import type { RadarObjectStoragePort, RadarSourcePort } from './ports.js';

export type RadarIngestOutcome = {
  replayed: boolean;
  snapshotId: string;
  status: 'stored' | 'processed';
  analysisReady: boolean;
  sha256: string;
  storageKey: string;
};

function extensionFor(format: 'gif' | 'png' | 'jpeg' | 'geotiff' | 'unknown') {
  if (format === 'jpeg') return 'jpg';
  if (format === 'geotiff') return 'tif';
  if (format === 'unknown') return 'bin';
  return format;
}

export async function runRadarIngestJob(
  pool: Pool,
  source: RadarSourcePort,
  storage: RadarObjectStoragePort,
  rawJob: RadarIngestJobPayload,
): Promise<RadarIngestOutcome> {
  const job = radarIngestJobPayloadSchema.parse(rawJob);
  const asset = await source.fetchNationalReflectivity();
  const metadata = radarSnapshotMetadataSchema.parse(asset.metadata);

  if (metadata.source !== job.source || metadata.product !== job.product) {
    throw new Error('Radar source returned metadata that does not match the requested job');
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
      analysisReady: existing.analysis_ready,
      sha256,
      storageKey: existing.storage_key,
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
    JSON.stringify({ requested_at: job.requested_at }),
  ]);

  const snapshotId = row.rows[0]?.id;
  if (!snapshotId) throw new Error('Radar snapshot insert did not return an id');

  const relativeKey = `${metadata.source}/${metadata.product}/${sha256}.${extensionFor(metadata.asset_format)}`;

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
      analysisReady: metadata.analysis_ready,
      sha256,
      storageKey: stored.storageKey,
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
