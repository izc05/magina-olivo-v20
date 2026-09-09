import { buildApp } from './app.js';
import { createDatabase } from './db/client.js';
import { createS3StorageFromEnv } from './storage/s3.js';

const databaseUrl = process.env.DATABASE_URL;
const db = databaseUrl ? createDatabase(databaseUrl) : null;
const storage = createS3StorageFromEnv() ?? undefined;
const app = buildApp({ db, storage });

const port = Number(process.env.PORT ?? 3001);
const host = process.env.HOST ?? '0.0.0.0';

async function start() {
  try {
    await app.listen({ port, host });
  } catch (error) {
    app.log.error(error);
    process.exit(1);
  }
}

async function shutdown(signal: string) {
  app.log.info({ signal }, 'shutting down');
  await app.close();
  if (db) await db.destroy();
  process.exit(0);
}

process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

void start();
