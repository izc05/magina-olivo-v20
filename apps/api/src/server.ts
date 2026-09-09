import { createJobBoss, startJobBoss } from '@magina/jobs';
import { buildApp } from './app.js';
import { createDatabase } from './db/client.js';
import { PgBossOcrQueue } from './ocr/pg-boss.js';
import { createS3StorageFromEnv } from './storage/s3.js';

const databaseUrl = process.env.DATABASE_URL;
const db = databaseUrl ? createDatabase(databaseUrl) : null;
const storage = createS3StorageFromEnv() ?? undefined;
const boss = databaseUrl ? createJobBoss(databaseUrl) : null;
const ocrQueue = boss ? new PgBossOcrQueue(boss) : undefined;
const app = buildApp({ db, storage, ocrQueue });

const port = Number(process.env.PORT ?? 3001);
const host = process.env.HOST ?? '0.0.0.0';

async function start() {
  try {
    if (boss) await startJobBoss(boss);
    await app.listen({ port, host });
  } catch (error) {
    app.log.error(error);
    if (boss) await boss.stop().catch(() => undefined);
    if (db) await db.destroy().catch(() => undefined);
    process.exit(1);
  }
}

async function shutdown(signal: string) {
  app.log.info({ signal }, 'shutting down');
  await app.close();
  if (boss) await boss.stop();
  if (db) await db.destroy();
  process.exit(0);
}

process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

void start();
