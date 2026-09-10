import {
  NOTIFICATION_DISPATCH_DEAD_LETTER_QUEUE_NAME,
  NOTIFICATION_DISPATCH_QUEUE_NAME,
  OCR_DEAD_LETTER_QUEUE_NAME,
  OCR_QUEUE_NAME,
  RADAR_INGEST_DEAD_LETTER_QUEUE_NAME,
  RADAR_INGEST_QUEUE_NAME,
  notificationDispatchJobPayloadSchema,
  ocrJobPayloadSchema,
  radarIngestJobPayloadSchema,
  type NotificationDispatchJobPayload,
  type OcrJobPayload,
  type RadarIngestJobPayload,
} from '@magina/contracts';
import { PgBoss } from 'pg-boss';

export {
  NOTIFICATION_DISPATCH_DEAD_LETTER_QUEUE_NAME,
  NOTIFICATION_DISPATCH_QUEUE_NAME,
  OCR_DEAD_LETTER_QUEUE_NAME,
  OCR_QUEUE_NAME,
  RADAR_INGEST_DEAD_LETTER_QUEUE_NAME,
  RADAR_INGEST_QUEUE_NAME,
};
export type { NotificationDispatchJobPayload, OcrJobPayload, RadarIngestJobPayload };
export type JobBoss = PgBoss;

export function createJobBoss(connectionString: string) {
  return new PgBoss(connectionString);
}

export async function startJobBoss(boss: PgBoss) {
  await boss.start();

  await boss.createQueue(OCR_DEAD_LETTER_QUEUE_NAME, {
    policy: 'standard', retryLimit: 0,
    retentionSeconds: 30 * 24 * 60 * 60, deleteAfterSeconds: 30 * 24 * 60 * 60,
  });
  await boss.createQueue(OCR_QUEUE_NAME, {
    policy: 'standard', retryLimit: 3, retryDelay: 30, retryBackoff: true,
    retryDelayMax: 10 * 60, expireInSeconds: 30 * 60, heartbeatSeconds: 60,
    retentionSeconds: 7 * 24 * 60 * 60, deleteAfterSeconds: 7 * 24 * 60 * 60,
    deadLetter: OCR_DEAD_LETTER_QUEUE_NAME,
  });

  await boss.createQueue(RADAR_INGEST_DEAD_LETTER_QUEUE_NAME, {
    policy: 'standard', retryLimit: 0,
    retentionSeconds: 7 * 24 * 60 * 60, deleteAfterSeconds: 7 * 24 * 60 * 60,
  });
  await boss.createQueue(RADAR_INGEST_QUEUE_NAME, {
    policy: 'standard', retryLimit: 3, retryDelay: 60, retryBackoff: true,
    retryDelayMax: 15 * 60, expireInSeconds: 10 * 60, heartbeatSeconds: 30,
    retentionSeconds: 3 * 24 * 60 * 60, deleteAfterSeconds: 3 * 24 * 60 * 60,
    deadLetter: RADAR_INGEST_DEAD_LETTER_QUEUE_NAME,
  });

  await boss.createQueue(NOTIFICATION_DISPATCH_DEAD_LETTER_QUEUE_NAME, {
    policy: 'standard', retryLimit: 0,
    retentionSeconds: 7 * 24 * 60 * 60, deleteAfterSeconds: 7 * 24 * 60 * 60,
  });
  await boss.createQueue(NOTIFICATION_DISPATCH_QUEUE_NAME, {
    policy: 'singleton', retryLimit: 2, retryDelay: 30, retryBackoff: true,
    retryDelayMax: 5 * 60, expireInSeconds: 5 * 60, heartbeatSeconds: 30,
    retentionSeconds: 3 * 24 * 60 * 60, deleteAfterSeconds: 3 * 24 * 60 * 60,
    deadLetter: NOTIFICATION_DISPATCH_DEAD_LETTER_QUEUE_NAME,
  });

  return boss;
}

export async function ensureNotificationDispatchSchedule(boss: PgBoss) {
  await boss.schedule(
    NOTIFICATION_DISPATCH_QUEUE_NAME,
    '* * * * *',
    { version: 1, limit: 50 },
    { key: 'pending-intents-v1', tz: 'UTC' },
  );
}

export async function enqueueOcrJob(boss: PgBoss, payload: OcrJobPayload) {
  const parsed = ocrJobPayloadSchema.parse(payload);
  const jobId = await boss.send(OCR_QUEUE_NAME, parsed);
  if (!jobId) throw new Error('pg-boss did not return an OCR job id');
  return jobId;
}

export async function enqueueRadarIngestJob(boss: PgBoss, payload: RadarIngestJobPayload) {
  const parsed = radarIngestJobPayloadSchema.parse(payload);
  const jobId = await boss.send(RADAR_INGEST_QUEUE_NAME, parsed);
  if (!jobId) throw new Error('pg-boss did not return a radar ingest job id');
  return jobId;
}

export async function enqueueNotificationDispatchJob(boss: PgBoss, payload: NotificationDispatchJobPayload) {
  const parsed = notificationDispatchJobPayloadSchema.parse(payload);
  const jobId = await boss.send(NOTIFICATION_DISPATCH_QUEUE_NAME, parsed);
  if (!jobId) throw new Error('pg-boss did not return a notification dispatch job id');
  return jobId;
}
