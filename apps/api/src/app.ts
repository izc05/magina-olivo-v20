import Fastify from 'fastify';
import type { DatabaseClient } from './db/client.js';
import { prototypeAuthWarning } from './request-context.js';
import { registerFieldRoutes } from './routes/fields.js';
import { registerIrrigationRoutes } from './routes/irrigations.js';
import { registerDomainRecordRoutes } from './routes/domain-records.js';
import { registerHarvestRoutes } from './routes/harvest.js';
import { registerDocumentRoutes } from './routes/documents.js';
import type { StoragePort } from './storage/port.js';
import { UnavailableStorage } from './storage/port.js';
import type { OcrQueuePort } from './ocr/port.js';
import { UnavailableOcrQueue } from './ocr/port.js';

export type AppDependencies = {
  db?: DatabaseClient | null;
  storage?: StoragePort;
  ocrQueue?: OcrQueuePort;
};

export function buildApp(dependencies: AppDependencies = {}) {
  const db = dependencies.db ?? null;
  const storage = dependencies.storage ?? new UnavailableStorage();
  const ocrQueue = dependencies.ocrQueue ?? new UnavailableOcrQueue();
  const app = Fastify({ logger: true });

  app.get('/health', async () => ({
    ok: true,
    service: 'magina-api',
    databaseConfigured: Boolean(db),
    warning: prototypeAuthWarning,
  }));

  registerFieldRoutes(app, db);
  registerIrrigationRoutes(app, db);
  registerDomainRecordRoutes(app, db);
  registerHarvestRoutes(app, db);
  registerDocumentRoutes(app, db, storage, ocrQueue);

  return app;
}
