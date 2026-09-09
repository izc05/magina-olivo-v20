import Fastify from 'fastify';
import type { DatabaseClient } from './db/client.js';
import { prototypeAuthWarning } from './request-context.js';
import { registerFieldRoutes } from './routes/fields.js';
import { registerIrrigationRoutes } from './routes/irrigations.js';
import { registerDomainRecordRoutes } from './routes/domain-records.js';
import { registerHarvestRoutes } from './routes/harvest.js';

export function buildApp(db: DatabaseClient | null = null) {
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

  return app;
}
