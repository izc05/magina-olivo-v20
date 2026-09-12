import { createJobBoss, startJobBoss } from '@magina/jobs';
import { buildApp } from './app.js';
import { createDatabase } from './db/client.js';
import { PgBossOcrQueue } from './ocr/pg-boss.js';
import { PgBossNotificationDispatchQueue } from './notifications/pg-boss.js';
import { PgBossRadarIngestQueue } from './radar/pg-boss.js';
import { createS3StorageFromEnv } from './storage/s3.js';
import { createGoogleIdentityVerifierFromEnv } from './auth/google.js';
import { assertSafeRuntimeEnvironment } from './runtime-security.js';

assertSafeRuntimeEnvironment();

const databaseUrl = process.env.DATABASE_URL;
const db = databaseUrl ? createDatabase(databaseUrl) : null;
const storage = createS3StorageFromEnv() ?? undefined;
const boss = databaseUrl ? createJobBoss(databaseUrl) : null;
const ocrQueue = boss ? new PgBossOcrQueue(boss) : undefined;
const notificationQueue = boss ? new PgBossNotificationDispatchQueue(boss) : undefined;
const radarQueue = boss ? new PgBossRadarIngestQueue(boss) : undefined;
const googleVerifier = createGoogleIdentityVerifierFromEnv() ?? undefined;
const pushPublicKey = process.env.VAPID_PUBLIC_KEY?.trim() || null;
const app = buildApp({ db, storage, ocrQueue, notificationQueue, radarQueue, googleVerifier, pushPublicKey });

const port = Number(process.env.PORT ?? 3001);
const host = process.env.HOST ?? '0.0.0.0';

async function start() {
  try {
    if (boss) await startJobBoss(boss);
    const address = await app.listen({ port, host });
    app.log.info({
      event: 'runtime_started',
      address,
      nodeEnv: process.env.NODE_ENV ?? 'development',
      nodeVersion: process.version,
      pid: process.pid,
      rateLimitEnabled: process.env.RATE_LIMIT_ENABLED ?? (process.env.NODE_ENV === 'production' ? 'true' : 'false'),
      trustProxy: process.env.TRUST_PROXY === 'true',
    }, 'magina api runtime started');
  } catch (error) {
    app.log.error(error);
    if (boss) await boss.stop().catch(() => undefined);
    if (db) await db.destroy().catch(() => undefined);
    process.exit(1);
  }
}

async function shutdown(signal: string) {
  app.log.info({ event: 'runtime_shutdown', signal }, 'shutting down');
  await app.close();
  if (boss) await boss.stop();
  if (db) await db.destroy();
  process.exit(0);
}

process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

void start();
