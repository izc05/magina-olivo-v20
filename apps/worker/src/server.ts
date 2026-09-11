import {
  financialAlertEvaluateJobPayloadSchema,
  notificationDispatchJobPayloadSchema,
  ocrJobPayloadSchema,
  radarIngestJobPayloadSchema,
  type FinancialAlertEvaluateJobPayload,
  type NotificationDispatchJobPayload,
  type OcrJobPayload,
  type RadarIngestJobPayload,
} from '@magina/contracts';
import {
  createJobBoss,
  ensureFinancialAlertEvaluationSchedule,
  ensureNotificationDispatchSchedule,
  FINANCIAL_ALERT_EVALUATE_QUEUE_NAME,
  NOTIFICATION_DISPATCH_QUEUE_NAME,
  OCR_QUEUE_NAME,
  RADAR_INGEST_QUEUE_NAME,
  startJobBoss,
} from '@magina/jobs';
import pg from 'pg';
import { createPushSenderFromEnv } from './notifications/web-push.js';
import { runFinancialAlertEvaluationJob } from './notifications/financial-evaluate.js';
import { runNotificationDispatchJob } from './notifications/dispatch.js';
import { DeterministicTestOcrProcessor, type OcrProcessorPort } from './ocr/processor.js';
import { runOcrJob } from './ocr/run-job.js';
import { createRadarS3StorageFromEnv, remoteAemetRadarSource } from './radar/adapters.js';
import { runRadarIngestJob } from './radar/run-job.js';

const { Pool } = pg;

type WorkerModule = 'ocr' | 'radar' | 'notifications';

function configuredModules(): Set<WorkerModule> {
  const raw = process.env.WORKER_MODULES?.trim();
  if (!raw) return new Set<WorkerModule>(['ocr']);

  const modules = new Set<WorkerModule>();
  for (const token of raw.split(',').map((value) => value.trim().toLowerCase()).filter(Boolean)) {
    if (token !== 'ocr' && token !== 'radar' && token !== 'notifications') {
      throw new Error(`Unsupported worker module: ${token}`);
    }
    modules.add(token);
  }
  if (modules.size === 0) throw new Error('WORKER_MODULES must enable at least one worker module');
  return modules;
}

function createProcessorFromEnv(): OcrProcessorPort {
  const mode = process.env.OCR_PROCESSOR_MODE;
  if (mode === 'test' && process.env.NODE_ENV !== 'production') {
    return new DeterministicTestOcrProcessor();
  }
  throw new Error('No production OCR processor is configured. Refusing to start OCR worker.');
}

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for @magina/worker');

const modules = configuredModules();
const boss = createJobBoss(databaseUrl);
const pool = new Pool({ connectionString: databaseUrl, max: 4 });
const processor = modules.has('ocr') ? createProcessorFromEnv() : null;
const radarStorage = modules.has('radar') ? createRadarS3StorageFromEnv() : null;
const pushSender = modules.has('notifications') ? createPushSenderFromEnv() : null;

if (modules.has('radar') && !radarStorage) {
  throw new Error('S3 storage configuration is required when WORKER_MODULES includes radar');
}
if (modules.has('notifications') && !pushSender) {
  throw new Error('VAPID_SUBJECT, VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY are required when WORKER_MODULES includes notifications');
}

async function start() {
  await startJobBoss(boss);

  if (modules.has('ocr')) {
    if (!processor) throw new Error('OCR processor was not initialized');
    await boss.work<OcrJobPayload>(OCR_QUEUE_NAME, { batchSize: 1 }, async ([job]) => {
      if (!job) return;
      const payload = ocrJobPayloadSchema.parse(job.data);
      await runOcrJob(pool, processor, payload);
    });
  }

  if (modules.has('radar')) {
    if (!radarStorage) throw new Error('Radar storage was not initialized');
    await boss.work<RadarIngestJobPayload>(RADAR_INGEST_QUEUE_NAME, { batchSize: 1 }, async ([job]) => {
      if (!job) return;
      const payload = radarIngestJobPayloadSchema.parse(job.data);
      await runRadarIngestJob(pool, remoteAemetRadarSource, radarStorage, payload);
    });
  }

  if (modules.has('notifications')) {
    if (!pushSender) throw new Error('Push sender was not initialized');
    await ensureNotificationDispatchSchedule(boss);
    await ensureFinancialAlertEvaluationSchedule(boss);

    await boss.work<FinancialAlertEvaluateJobPayload>(FINANCIAL_ALERT_EVALUATE_QUEUE_NAME, { batchSize: 1 }, async ([job]) => {
      if (!job) return;
      const payload = financialAlertEvaluateJobPayloadSchema.parse(job.data);
      await runFinancialAlertEvaluationJob(pool, payload);
    });

    await boss.work<NotificationDispatchJobPayload>(NOTIFICATION_DISPATCH_QUEUE_NAME, { batchSize: 1 }, async ([job]) => {
      if (!job) return;
      const payload = notificationDispatchJobPayloadSchema.parse(job.data);
      await runNotificationDispatchJob(pool, pushSender, payload);
    });
  }

  console.log(`Mágina worker listening: ${[...modules].join(', ')}`);
}

async function shutdown(signal: string) {
  console.log(`Mágina worker shutting down: ${signal}`);
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
