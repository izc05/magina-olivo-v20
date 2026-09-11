import Fastify from 'fastify';
import cookie from '@fastify/cookie';
import cors from '@fastify/cors';
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
import { registerHarvestSettlementCandidateRoutes } from './routes/harvest-settlement-candidates.js';
import { registerHarvestFieldCommercialRoutes } from './routes/harvest-field-commercial.js';
import { registerFarmEconomicsRoutes } from './routes/farm-economics.js';
import { registerCampaignRoutes } from './routes/campaigns.js';
import { registerAgendaRoutes } from './routes/agenda.js';
import { registerAgronomyRoutes } from './routes/agronomy.js';
import { registerAgronomyAlertRoutes } from './routes/agronomy-alerts.js';
import { registerAttentionRoutes } from './routes/attention.js';
import { registerFinancialAttentionRoutes } from './routes/financial-attention.js';
import { registerHomePriorityPreferenceRoutes } from './routes/home-priority-preferences.js';
import { registerFinancialNotificationRoutes } from './routes/financial-notifications.js';
import { registerCommercialNotificationRoutes } from './routes/commercial-notifications.js';
import { registerPlannedTaskRoutes } from './routes/planned-tasks.js';
import { registerDocumentRoutes } from './routes/documents.js';
import { registerDocumentAccessRoutes } from './routes/document-access.js';
import { registerDocumentCatalogRoutes } from './routes/document-catalog.js';
import { registerDocumentAnalysisRoutes } from './routes/document-analysis.js';
import { registerGisRoutes } from './routes/gis.js';
import { registerTerritoryRoutes } from './routes/territory.js';
import { registerWeatherRoutes } from './routes/weather.js';
import { registerRadarRoutes } from './routes/radar.js';
import { registerPushRoutes } from './routes/push.js';
import { registerWorkRoutes } from './routes/work.js';
import { registerWorkCommercialRoutes } from './routes/work-commercial.js';
import { registerProfessionalRoutes } from './routes/professional.js';
import { registerProfessionalInvoiceRoutes } from './routes/professional-invoices.js';
import { registerProfessionalQuoteRoutes } from './routes/professional-quotes.js';
import { registerProfessionalCustomerRoutes } from './routes/professional-customers.js';
import { registerProfessionalAttentionRoutes } from './routes/professional-attention.js';
import { registerProfessionalBusinessProfileRoutes } from './routes/professional-business-profile.js';
import { registerProfessionalPrintRoutes } from './routes/professional-print.js';
import { registerProfessionalDeliveryRoutes } from './routes/professional-deliveries.js';
import { registerProfessionalShareLinkRoutes } from './routes/professional-share-links.js';
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

function corsOrigins() {
  const configured = process.env.CORS_ALLOWED_ORIGINS
    ?.split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);

  if (configured?.length) return configured;
  if (process.env.NODE_ENV === 'production') return [];
  return ['http://127.0.0.1:3000', 'http://localhost:3000'];
}

function isPrivateApiPath(url: string) {
  return url.startsWith('/api/v1/') && !url.startsWith('/api/v1/public/');
}

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
  const allowedOrigins = corsOrigins();

  app.register(cors, {
    credentials: true,
    methods: ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['content-type', 'x-workspace-id', 'x-user-id'],
    origin(origin, callback) {
      if (!origin || allowedOrigins.includes(origin)) {
        callback(null, true);
        return;
      }
      callback(new Error('origin_not_allowed'), false);
    },
  });
  app.register(cookie);
  app.addHook('onRequest', async (request) => {
    await hydrateRequestAuthentication(request, db);
  });
  app.addHook('onSend', async (request, reply, payload) => {
    reply.header('x-content-type-options', 'nosniff');
    reply.header('referrer-policy', 'strict-origin-when-cross-origin');
    reply.header('x-frame-options', 'DENY');
    reply.header('permissions-policy', 'camera=(), microphone=(), geolocation=(self)');
    if (isPrivateApiPath(request.url)) reply.header('cache-control', 'no-store');
    return payload;
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
  registerHarvestSettlementCandidateRoutes(app, db);
  registerHarvestFieldCommercialRoutes(app, db);
  registerFarmEconomicsRoutes(app, db);
  registerCampaignRoutes(app, db);
  registerAgendaRoutes(app, db);
  registerAgronomyRoutes(app, db, weatherProvider);
  registerAgronomyAlertRoutes(app, db, weatherProvider, notificationQueue);
  registerAttentionRoutes(app, db, weatherProvider);
  registerFinancialAttentionRoutes(app, db);
  registerHomePriorityPreferenceRoutes(app, db);
  registerFinancialNotificationRoutes(app, db, notificationQueue);
  registerCommercialNotificationRoutes(app, db);
  registerPlannedTaskRoutes(app, db);
  registerWorkRoutes(app, db);
  registerWorkCommercialRoutes(app, db);
  registerProfessionalRoutes(app, db);
  registerProfessionalInvoiceRoutes(app, db);
  registerProfessionalQuoteRoutes(app, db);
  registerProfessionalCustomerRoutes(app, db);
  registerProfessionalAttentionRoutes(app, db);
  registerProfessionalBusinessProfileRoutes(app, db);
  registerProfessionalPrintRoutes(app, db);
  registerProfessionalDeliveryRoutes(app, db);
  registerProfessionalShareLinkRoutes(app, db, storage);
  registerDocumentRoutes(app, db, storage, ocrQueue);
  registerDocumentAccessRoutes(app, db, storage);
  registerDocumentCatalogRoutes(app, db);
  registerDocumentAnalysisRoutes(app, db);
  registerGisRoutes(app, db, gisProviders);

  return app;
}
