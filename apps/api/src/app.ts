import Fastify from 'fastify';
import cookie from '@fastify/cookie';
import type { DatabaseClient } from './db/client.js';
import { hydrateRequestAuthentication, prototypeAuthWarning } from './request-context.js';
import { registerAuthRoutes } from './routes/auth.js';
import { registerMeRoutes } from './routes/me.js';
import { registerFieldRoutes } from './routes/fields.js';
import { registerIrrigationRoutes } from './routes/irrigations.js';
import { registerObservationRoutes } from './routes/observations.js';
import { registerDomainRecordRoutes } from './routes/domain-records.js';
import { registerHarvestRoutes } from './routes/harvest.js';
import { registerHarvestCommercialRoutes } from './routes/harvest-commercial.js';
import { registerHarvestFieldCommercialRoutes } from './routes/harvest-field-commercial.js';
import { registerFarmEconomicsRoutes } from './routes/farm-economics.js';
import { registerCampaignRoutes } from './routes/campaigns.js';
import { registerAgendaRoutes } from './routes/agenda.js';
import { registerAgronomyRoutes } from './routes/agronomy.js';
import { registerAgronomyAlertRoutes } from './routes/agronomy-alerts.js';
import { registerAttentionRoutes } from './routes/attention.js';
import { registerPlannedTaskRoutes } from './routes/planned-tasks.js';
import { registerDocumentRoutes } from './routes/documents.js';
import { registerGisRoutes } from './routes/gis.js';
import { registerTerritoryRoutes } from './routes/territory.js';
import { registerWeatherRoutes } from './routes/weather.js';
import { registerRadarRoutes } from './routes/radar.js';
import { registerPushRoutes } from './routes/push.js';
import { registerWorkRoutes } from './routes/work.js';
import { registerWorkCommercialRoutes } from './routes/work-commercial.js';
import { registerProfessionalRoutes } from './routes/professional.js';
import type { StoragePort } from './storage/port.js';
import { UnavailableStorage } from './storage/port.js';
import type { OcrQueuePort } from './ocr/port.js';
import { UnavailableOcrQueue } from './ocr/port.js';
import type { NotificationDispatchQueuePort } from './notifications/port.js';
import { UnavailableNotificationDispatchQueue } from './notifications/port.js';
import type { GoogleIdentityVerifier } from './auth/google.js';
import { UnavailableGoogleIdentityVerifier } from './auth/google.js';
import type { GisProviders } from './gis/providers.js';
import { remoteGisProviders } from './gis/providers.js';
import type { MunicipalityWeatherProvider } from './weather/providers.js';
import { remoteAemetWeatherProvider } from './weather/providers.js';

export type AppDependencies = {
  db?: DatabaseClient | null;
  storage?: StoragePort;
  ocrQueue?: OcrQueuePort;
  notificationQueue?: NotificationDispatchQueuePort;
  pushPublicKey?: string | null;
  googleVerifier?: GoogleIdentityVerifier;
  gisProviders?: GisProviders;
  weatherProvider?: MunicipalityWeatherProvider;
};

export function buildApp(dependencies: AppDependencies = {}) {
  const db = dependencies.db ?? null;
  const storage = dependencies.storage ?? new UnavailableStorage();
  const ocrQueue = dependencies.ocrQueue ?? new UnavailableOcrQueue();
  const notificationQueue = dependencies.notificationQueue ?? new UnavailableNotificationDispatchQueue();
  const pushPublicKey = dependencies.pushPublicKey ?? null;
  const googleVerifier = dependencies.googleVerifier ?? new UnavailableGoogleIdentityVerifier();
  const gisProviders = dependencies.gisProviders ?? remoteGisProviders;
  const weatherProvider = dependencies.weatherProvider ?? remoteAemetWeatherProvider;
  const app = Fastify({ logger: true });

  app.register(cookie);
  app.addHook('onRequest', async (request) => {
    await hydrateRequestAuthentication(request, db);
  });

  app.get('/health', async () => ({
    ok: true,
    service: 'magina-api',
    databaseConfigured: Boolean(db),
    googleAuthConfigured: !(googleVerifier instanceof UnavailableGoogleIdentityVerifier),
    webPushConfigured: Boolean(pushPublicKey),
    warning: prototypeAuthWarning,
  }));

  registerAuthRoutes(app, db, googleVerifier);
  registerMeRoutes(app, db);
  registerTerritoryRoutes(app, db);
  registerWeatherRoutes(app, db, weatherProvider);
  registerRadarRoutes(app, db);
  registerPushRoutes(app, db, notificationQueue, pushPublicKey);
  registerFieldRoutes(app, db);
  registerIrrigationRoutes(app, db);
  registerObservationRoutes(app, db);
  registerDomainRecordRoutes(app, db);
  registerHarvestRoutes(app, db);
  registerHarvestCommercialRoutes(app, db);
  registerHarvestFieldCommercialRoutes(app, db);
  registerFarmEconomicsRoutes(app, db);
  registerCampaignRoutes(app, db);
  registerAgendaRoutes(app, db);
  registerAgronomyRoutes(app, db, weatherProvider);
  registerAgronomyAlertRoutes(app, db, weatherProvider, notificationQueue);
  registerAttentionRoutes(app, db, weatherProvider);
  registerPlannedTaskRoutes(app, db);
  registerWorkRoutes(app, db);
  registerWorkCommercialRoutes(app, db);
  registerProfessionalRoutes(app, db);
  registerDocumentRoutes(app, db, storage, ocrQueue);
  registerGisRoutes(app, db, gisProviders);

  return app;
}
