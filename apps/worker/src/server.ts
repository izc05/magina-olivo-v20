import { ocrJobPayloadSchema, type OcrJobPayload } from '@magina/contracts';
import { createJobBoss, OCR_QUEUE_NAME, startJobBoss } from '@magina/jobs';
import pg from 'pg';
import { DeterministicTestOcrProcessor, type OcrProcessorPort } from './ocr/processor.js';
import { runOcrJob } from './ocr/run-job.js';

const { Pool } = pg;

function createProcessorFromEnv(): OcrProcessorPort {
  const mode = process.env.OCR_PROCESSOR_MODE;
  if (mode === 'test' && process.env.NODE_ENV !== 'production') {
    return new DeterministicTestOcrProcessor();
  }
  throw new Error('No production OCR processor is configured. Refusing to start OCR worker.');
}

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for @magina/worker');

const boss = createJobBoss(databaseUrl);
const pool = new Pool({ connectionString: databaseUrl, max: 4 });
const processor = createProcessorFromEnv();

async function start() {
  await startJobBoss(boss);

  await boss.work<OcrJobPayload>(OCR_QUEUE_NAME, { batchSize: 1 }, async ([job]) => {
    if (!job) return;
    const payload = ocrJobPayloadSchema.parse(job.data);
    await runOcrJob(pool, processor, payload);
  });

  console.log(`Mágina OCR worker listening on queue ${OCR_QUEUE_NAME}`);
}

async function shutdown(signal: string) {
  console.log(`Mágina OCR worker shutting down: ${signal}`);
  await boss.stop();
  await pool.end();
  process.exit(0);
}

process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

start().catch(async (error) => {
  console.error(error);
  await boss.stop().catch(() => undefined);
  await pool.end().catch(() => undefined);
  process.exit(1);
});
