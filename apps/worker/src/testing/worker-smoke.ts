import {
  createJobBoss,
  enqueueOcrJob,
  OCR_QUEUE_NAME,
  startJobBoss,
} from '@magina/jobs';
import { ocrJobPayloadSchema, type OcrJobPayload } from '@magina/contracts';
import pg from 'pg';
import { DeterministicTestOcrProcessor } from '../ocr/processor.js';
import { runOcrJob } from '../ocr/run-job.js';

const { Pool } = pg;
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required');

const pool = new Pool({ connectionString: databaseUrl, max: 2 });
const boss = createJobBoss(databaseUrl);
const processor = new DeterministicTestOcrProcessor();

const jobPayload: OcrJobPayload = {
  version: 1,
  ocr_run_id: '12121212-1212-4121-8121-121212121212',
  document_version_id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
  storage_key: 'test/11111111-1111-4111-8111-111111111111/dddddddd-dddd-4ddd-8ddd-dddddddddddd/eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
  mime_type: 'image/jpeg',
  expected_sha256_hex: 'a'.repeat(64),
  preferred_provider: 'auto',
};

async function waitForSuccess() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const result = await pool.query<{ status: string; raw_text: string | null; provider_version: string | null }>(
      'SELECT status, raw_text, provider_version FROM ocr_runs WHERE id = $1',
      [jobPayload.ocr_run_id],
    );
    const row = result.rows[0];
    if (row?.status === 'succeeded') return row;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error('Timed out waiting for OCR worker success');
}

async function main() {
  await startJobBoss(boss);

  await boss.work<OcrJobPayload>(OCR_QUEUE_NAME, { batchSize: 1 }, async ([job]) => {
    if (!job) return;
    const payload = ocrJobPayloadSchema.parse(job.data);
    await runOcrJob(pool, processor, payload);
  });

  await enqueueOcrJob(boss, jobPayload);
  const succeeded = await waitForSuccess();

  if (!succeeded.raw_text?.includes('1842 KG')) throw new Error('Worker did not persist OCR text');
  if (succeeded.provider_version !== 'test-double') throw new Error('Worker provider version was not persisted');

  const replay = await runOcrJob(pool, processor, jobPayload);
  if (!replay.replayed) throw new Error('Succeeded OCR run was not idempotent');

  console.log('pg-boss OCR worker smoke test passed');
}

try {
  await main();
} finally {
  await boss.stop().catch(() => undefined);
  await pool.end();
}
