import { ocrJobPayloadSchema, type OcrJobPayload } from '@magina/contracts';
import type { Pool } from 'pg';
import type { OcrProcessorPort } from './processor.js';

export type OcrJobOutcome = {
  replayed: boolean;
  ocrRunId: string;
  status: 'succeeded';
};

export async function runOcrJob(
  pool: Pool,
  processor: OcrProcessorPort,
  rawJob: OcrJobPayload,
): Promise<OcrJobOutcome> {
  const job = ocrJobPayloadSchema.parse(rawJob);

  const currentResult = await pool.query<{
    id: string;
    status: 'queued' | 'processing' | 'succeeded' | 'failed';
    document_version_id: string;
    storage_key: string;
    sha256: string;
    upload_status: string;
    integrity_status: string;
  }>(`
    SELECT
      o.id,
      o.status,
      o.document_version_id,
      dv.storage_key,
      dv.sha256,
      dv.upload_status,
      dv.integrity_status
    FROM ocr_runs o
    JOIN document_versions dv ON dv.id = o.document_version_id
    WHERE o.id = $1
  `, [job.ocr_run_id]);

  const current = currentResult.rows[0];
  if (!current) throw new Error(`OCR run not found: ${job.ocr_run_id}`);

  if (current.status === 'succeeded') {
    return { replayed: true, ocrRunId: job.ocr_run_id, status: 'succeeded' };
  }

  if (current.document_version_id !== job.document_version_id) throw new Error('OCR job document version mismatch');
  if (current.storage_key !== job.storage_key) throw new Error('OCR job storage key mismatch');
  if (current.sha256.toLowerCase() !== job.expected_sha256_hex.toLowerCase()) throw new Error('OCR job SHA-256 mismatch');
  if (current.upload_status !== 'uploaded') throw new Error(`OCR document is not uploaded: ${current.upload_status}`);
  if (current.integrity_status === 'failed') throw new Error('OCR document integrity check failed');

  await pool.query(`
    UPDATE ocr_runs
    SET status = 'processing',
        started_at = COALESCE(started_at, now()),
        completed_at = NULL,
        error_code = NULL,
        error_message = NULL
    WHERE id = $1
  `, [job.ocr_run_id]);

  try {
    const result = await processor.process(job);
    const confidence = result.confidence == null
      ? null
      : Math.max(0, Math.min(1, result.confidence));

    await pool.query(`
      UPDATE ocr_runs
      SET status = 'succeeded',
          provider = $2,
          provider_version = $3,
          raw_text = $4,
          confidence = $5,
          completed_at = now(),
          error_code = NULL,
          error_message = NULL
      WHERE id = $1
    `, [
      job.ocr_run_id,
      result.provider,
      result.providerVersion ?? null,
      result.rawText,
      confidence,
    ]);

    return { replayed: false, ocrRunId: job.ocr_run_id, status: 'succeeded' };
  } catch (error) {
    await pool.query(`
      UPDATE ocr_runs
      SET status = 'failed',
          completed_at = now(),
          error_code = 'processor_error',
          error_message = $2
      WHERE id = $1
    `, [job.ocr_run_id, error instanceof Error ? error.message : 'Unknown OCR processor error']);
    throw error;
  }
}
