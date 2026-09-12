import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';

const datasetSchema = z.enum([
  'users',
  'workspaces',
  'fields',
  'campaigns',
  'work',
  'irrigation',
  'treatments',
  'fertilization',
  'pruning',
  'expenses',
  'harvest',
  'settlements',
  'collections',
  'agenda',
  'parties',
  'machinery',
  'materials',
  'documents',
  'ocr',
  'content',
  'invoices',
  'quotes',
  'plans',
  'territory',
  'market',
]);

const datasetLabels: Record<z.infer<typeof datasetSchema>, string> = {
  users: 'Usuarios',
  workspaces: 'Espacios de trabajo',
  fields: 'Fincas',
  campaigns: 'Campañas agrícolas',
  work: 'Trabajos agrícolas',
  irrigation: 'Riegos',
  treatments: 'Tratamientos',
  fertilization: 'Abonado',
  pruning: 'Poda',
  expenses: 'Gastos',
  harvest: 'Entregas de cosecha',
  settlements: 'Liquidaciones',
  collections: 'Cobros de cosecha',
  agenda: 'Agenda y tareas',
  parties: 'Personas y empresas',
  machinery: 'Maquinaria',
  materials: 'Materiales',
  documents: 'Documentos',
  ocr: 'Procesos OCR',
  content: 'Contenido público',
  invoices: 'Facturas profesionales',
  quotes: 'Presupuestos profesionales',
  plans: 'Planes y suscripciones',
  territory: 'Territorio',
  market: 'Histórico de mercado',
};

function isSafeLaunchUrl(value: string) {
  if (value.startsWith('/') && !value.startsWith('//')) return true;
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:';
  } catch {
    return false;
  }
}

const externalAppSchema = z.object({
  enabled: z.boolean(),
  name: z.string().trim().min(1).max(100),
  description: z.string().trim().max(500).nullable().optional(),
  url: z.string().trim().max(2000).refine(isSafeLaunchUrl, 'invalid_url'),
  mode: z.enum(['new_tab', 'embedded']),
  health_url: z.string().trim().max(2000).refine(isSafeLaunchUrl, 'invalid_health_url').nullable().optional(),
});

export type AdminExternalAppConfig = z.infer<typeof externalAppSchema>;

const defaultExternalApp: AdminExternalAppConfig = {
  enabled: false,
  name: 'Aplicación externa',
  description: 'Acceso administrado a una aplicación complementaria de Mágina Olivo.',
  url: '/mi-campo',
  mode: 'new_tab',
  health_url: null,
};

type OperationsMetrics = {
  users_total: number;
  users_active: number;
  users_suspended: number;
  users_new_30d: number;
  workspaces_total: number;
  workspaces_professional: number;
  fields_active: number;
  field_area_ha: number;
  campaigns_active: number;
  work_records_total: number;
  irrigation_total: number;
  treatment_total: number;
  fertilization_total: number;
  pruning_total: number;
  expenses_total: number;
  expenses_eur: number;
  harvest_kg: number;
  settlements_total: number;
  settlements_net_eur: number;
  collections_total: number;
  collections_eur: number;
  scheduled_open: number;
  parties_active: number;
  documents_active: number;
  document_bytes: number;
  ocr_pending: number;
  ocr_failed: number;
  content_draft: number;
  content_published: number;
  invoices_issued: number;
  invoiced_eur: number;
  quotes_open: number;
  quotes_accepted: number;
  plans_pro: number;
  plans_professional: number;
  market_observations: number;
  market_latest_period: string | null;
  weather_stale: number;
  admin_actions_24h: number;
};

async function operationsMetrics(db: DatabaseClient): Promise<OperationsMetrics> {
  const result = await sql<OperationsMetrics>`
    SELECT
      (SELECT count(*)::int FROM users WHERE status <> 'deleted') AS users_total,
      (SELECT count(*)::int FROM users WHERE status = 'active') AS users_active,
      (SELECT count(*)::int FROM users WHERE status = 'suspended') AS users_suspended,
      (SELECT count(*)::int FROM users WHERE created_at >= now() - interval '30 days' AND status <> 'deleted') AS users_new_30d,
      (SELECT count(*)::int FROM workspaces) AS workspaces_total,
      (SELECT count(*)::int FROM workspaces WHERE type = 'professional') AS workspaces_professional,
      (SELECT count(*)::int FROM fields WHERE status = 'active') AS fields_active,
      (SELECT coalesce(sum(calculated_area_ha), 0)::float8 FROM fields WHERE status = 'active') AS field_area_ha,
      (SELECT count(*)::int FROM campaigns WHERE status = 'active') AS campaigns_active,
      (SELECT count(*)::int FROM work_records) AS work_records_total,
      (SELECT count(*)::int FROM irrigation_records) AS irrigation_total,
      (SELECT count(*)::int FROM treatment_records) AS treatment_total,
      (SELECT count(*)::int FROM fertilization_records) AS fertilization_total,
      (SELECT count(*)::int FROM pruning_records) AS pruning_total,
      (SELECT count(*)::int FROM expense_records) AS expenses_total,
      (SELECT coalesce(sum(amount_eur), 0)::float8 FROM expense_records) AS expenses_eur,
      (SELECT coalesce(sum(total_kg), 0)::float8 FROM harvest_deliveries) AS harvest_kg,
      (SELECT count(*)::int FROM harvest_settlements WHERE status = 'confirmed') AS settlements_total,
      (SELECT coalesce(sum(net_eur), 0)::float8 FROM harvest_settlements WHERE status = 'confirmed') AS settlements_net_eur,
      (SELECT count(*)::int FROM harvest_collections) AS collections_total,
      (SELECT coalesce(sum(amount_eur), 0)::float8 FROM harvest_collections) AS collections_eur,
      (SELECT count(*)::int FROM scheduled_events WHERE status IN ('planned', 'postponed')) AS scheduled_open,
      (SELECT count(*)::int FROM parties WHERE active = true) AS parties_active,
      (SELECT count(*)::int FROM documents WHERE status = 'active') AS documents_active,
      (SELECT coalesce(sum(byte_size), 0)::float8 FROM document_versions WHERE upload_status = 'uploaded') AS document_bytes,
      (SELECT count(*)::int FROM ocr_runs WHERE status IN ('queued', 'processing')) AS ocr_pending,
      (SELECT count(*)::int FROM ocr_runs WHERE status = 'failed') AS ocr_failed,
      (SELECT count(*)::int FROM cms_entries WHERE status = 'draft') AS content_draft,
      (SELECT count(*)::int FROM cms_entries WHERE status = 'published') AS content_published,
      (SELECT count(*)::int FROM professional_invoices WHERE status = 'issued') AS invoices_issued,
      (SELECT coalesce(sum(total_eur), 0)::float8 FROM professional_invoices WHERE status = 'issued') AS invoiced_eur,
      (SELECT count(*)::int FROM professional_quotes WHERE status IN ('draft', 'sent')) AS quotes_open,
      (SELECT count(*)::int FROM professional_quotes WHERE status = 'accepted') AS quotes_accepted,
      (SELECT count(*)::int FROM workspace_plan_subscriptions WHERE status IN ('active', 'trialing') AND plan_code = 'pro') AS plans_pro,
      (SELECT count(*)::int FROM workspace_plan_subscriptions WHERE status IN ('active', 'trialing') AND plan_code = 'professional') AS plans_professional,
      (SELECT count(*)::int FROM market_olive_oil_weekly WHERE status = 'validated') AS market_observations,
      (SELECT max(period_end)::text FROM market_olive_oil_weekly WHERE status = 'validated') AS market_latest_period,
      (SELECT count(*)::int FROM weather_forecast_cache WHERE expires_at < now()) AS weather_stale,
      (SELECT count(*)::int FROM admin_audit_log WHERE created_at >= now() - interval '24 hours') AS admin_actions_24h
  `.execute(db);
  return result.rows[0];
}

async function externalAppConfig(db: DatabaseClient): Promise<AdminExternalAppConfig> {
  const result = await sql<{ value_json: unknown }>`
    SELECT value_json FROM site_settings WHERE key = 'platform.external_app' LIMIT 1
  `.execute(db);
  const parsed = externalAppSchema.safeParse(result.rows[0]?.value_json);
  return parsed.success ? parsed.data : defaultExternalApp;
}

function datasetCatalog(metrics: OperationsMetrics) {
  return [
    { id: 'users', label: datasetLabels.users, count: metrics.users_total, sensitivity: 'restricted' },
    { id: 'workspaces', label: datasetLabels.workspaces, count: metrics.workspaces_total, sensitivity: 'restricted' },
    { id: 'fields', label: datasetLabels.fields, count: metrics.fields_active, sensitivity: 'restricted' },
    { id: 'campaigns', label: datasetLabels.campaigns, count: metrics.campaigns_active, sensitivity: 'restricted' },
    { id: 'work', label: datasetLabels.work, count: metrics.work_records_total, sensitivity: 'restricted' },
    { id: 'irrigation', label: datasetLabels.irrigation, count: metrics.irrigation_total, sensitivity: 'restricted' },
    { id: 'treatments', label: datasetLabels.treatments, count: metrics.treatment_total, sensitivity: 'restricted' },
    { id: 'fertilization', label: datasetLabels.fertilization, count: metrics.fertilization_total, sensitivity: 'restricted' },
    { id: 'pruning', label: datasetLabels.pruning, count: metrics.pruning_total, sensitivity: 'restricted' },
    { id: 'expenses', label: datasetLabels.expenses, count: metrics.expenses_total, sensitivity: 'financial' },
    { id: 'harvest', label: datasetLabels.harvest, count: null, sensitivity: 'restricted' },
    { id: 'settlements', label: datasetLabels.settlements, count: metrics.settlements_total, sensitivity: 'financial' },
    { id: 'collections', label: datasetLabels.collections, count: metrics.collections_total, sensitivity: 'financial' },
    { id: 'agenda', label: datasetLabels.agenda, count: metrics.scheduled_open, sensitivity: 'restricted' },
    { id: 'parties', label: datasetLabels.parties, count: metrics.parties_active, sensitivity: 'restricted' },
    { id: 'machinery', label: datasetLabels.machinery, count: null, sensitivity: 'restricted' },
    { id: 'materials', label: datasetLabels.materials, count: null, sensitivity: 'restricted' },
    { id: 'documents', label: datasetLabels.documents, count: metrics.documents_active, sensitivity: 'restricted' },
    { id: 'ocr', label: datasetLabels.ocr, count: metrics.ocr_pending + metrics.ocr_failed, sensitivity: 'restricted' },
    { id: 'content', label: datasetLabels.content, count: metrics.content_draft + metrics.content_published, sensitivity: 'platform' },
    { id: 'invoices', label: datasetLabels.invoices, count: metrics.invoices_issued, sensitivity: 'financial' },
    { id: 'quotes', label: datasetLabels.quotes, count: metrics.quotes_open + metrics.quotes_accepted, sensitivity: 'financial' },
    { id: 'plans', label: datasetLabels.plans, count: metrics.plans_pro + metrics.plans_professional, sensitivity: 'platform' },
    { id: 'territory', label: datasetLabels.territory, count: null, sensitivity: 'public' },
    { id: 'market', label: datasetLabels.market, count: metrics.market_observations, sensitivity: 'public' },
  ];
}

async function readDataset(db: DatabaseClient, dataset: z.infer<typeof datasetSchema>, limit: number) {
  switch (dataset) {
    case 'users':
      return db.selectFrom('users')
        .select(['id', 'display_name', 'primary_email', 'status', 'created_at', 'last_login_at'])
        .where('status', '<>', 'deleted')
        .orderBy('created_at', 'desc')
        .limit(limit)
        .execute();
    case 'workspaces':
      return db.selectFrom('workspaces')
        .select(['id', 'name', 'type', 'created_at', 'updated_at'])
        .orderBy('created_at', 'desc')
        .limit(limit)
        .execute();
    case 'fields':
      return db.selectFrom('fields')
        .select(['id', 'workspace_id', 'name', 'municipality', 'province', 'calculated_area_ha', 'geometry_source', 'geometry_status', 'status', 'updated_at'])
        .orderBy('updated_at', 'desc')
        .limit(limit)
        .execute();
    case 'campaigns':
      return db.selectFrom('campaigns')
        .select(['id', 'workspace_id', 'name', 'start_date', 'end_date', 'status', 'created_at'])
        .orderBy('start_date', 'desc')
        .limit(limit)
        .execute();
    case 'irrigation':
      return db.selectFrom('irrigation_records')
        .select(['id', 'workspace_id', 'field_id', 'campaign_id', 'occurred_at', 'duration_hours', 'water_m3', 'cost_eur', 'created_at'])
        .orderBy('occurred_at', 'desc')
        .limit(limit)
        .execute();
    case 'treatments':
      return db.selectFrom('treatment_records')
        .select(['id', 'workspace_id', 'field_id', 'campaign_id', 'occurred_at', 'reason', 'product_name', 'dose', 'quantity', 'applicator', 'equipment', 'cost_eur', 'created_at'])
        .orderBy('occurred_at', 'desc')
        .limit(limit)
        .execute();
    case 'fertilization':
      return db.selectFrom('fertilization_records')
        .select(['id', 'workspace_id', 'field_id', 'campaign_id', 'occurred_at', 'product_name', 'quantity_kg', 'application_method', 'composition', 'cost_eur', 'supplier', 'created_at'])
        .orderBy('occurred_at', 'desc')
        .limit(limit)
        .execute();
    case 'pruning':
      return db.selectFrom('pruning_records')
        .select(['id', 'workspace_id', 'field_id', 'campaign_id', 'occurred_at', 'pruning_type', 'workers', 'hours', 'cost_eur', 'created_at'])
        .orderBy('occurred_at', 'desc')
        .limit(limit)
        .execute();
    case 'expenses':
      return db.selectFrom('expense_records')
        .select(['id', 'workspace_id', 'field_id', 'campaign_id', 'occurred_on', 'category', 'concept', 'amount_eur', 'created_at'])
        .orderBy('occurred_on', 'desc')
        .limit(limit)
        .execute();
    case 'harvest':
      return db.selectFrom('harvest_deliveries')
        .select(['id', 'workspace_id', 'campaign_id', 'cooperative_or_mill', 'delivery_at', 'ticket_number', 'total_kg', 'source', 'created_at'])
        .orderBy('delivery_at', 'desc')
        .limit(limit)
        .execute();
    case 'agenda':
      return db.selectFrom('scheduled_events')
        .select(['id', 'workspace_id', 'field_id', 'title', 'scheduled_at', 'status', 'source', 'task_kind', 'created_at', 'updated_at'])
        .orderBy('scheduled_at', 'desc')
        .limit(limit)
        .execute();
    case 'documents':
      return db.selectFrom('documents')
        .select(['id', 'workspace_id', 'kind', 'title', 'status', 'created_at', 'archived_at'])
        .orderBy('created_at', 'desc')
        .limit(limit)
        .execute();
    case 'ocr':
      return db.selectFrom('ocr_runs')
        .select(['id', 'workspace_id', 'provider', 'provider_version', 'status', 'confidence', 'error_code', 'created_at', 'started_at', 'completed_at'])
        .orderBy('created_at', 'desc')
        .limit(limit)
        .execute();
    case 'content':
      return db.selectFrom('cms_entries')
        .select(['id', 'type', 'slug', 'title', 'status', 'featured', 'starts_at', 'ends_at', 'updated_at', 'published_at'])
        .orderBy('updated_at', 'desc')
        .limit(limit)
        .execute();
    case 'work': {
      const result = await sql`
        SELECT id, workspace_id, field_id, campaign_id, type, occurred_on, title, performed_for, customer_party_id, quoted_amount_eur, charge_eur, created_at, updated_at
        FROM work_records
        ORDER BY occurred_on DESC, created_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'settlements': {
      const result = await sql`
        SELECT id, workspace_id, campaign_id, counterparty_name, settlement_number, settled_on, basis, unit_price_eur, gross_eur, deductions_eur, net_eur, status, created_at
        FROM harvest_settlements
        ORDER BY settled_on DESC, created_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'collections': {
      const result = await sql`
        SELECT id, workspace_id, settlement_id, collected_on, amount_eur, method, reference, created_at
        FROM harvest_collections
        ORDER BY collected_on DESC, created_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'parties': {
      const result = await sql`
        SELECT id, workspace_id, kind, display_name, legal_name, phone, email, roles, active, created_at, updated_at
        FROM parties
        ORDER BY updated_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'machinery': {
      const result = await sql`
        SELECT id, workspace_id, name, category, ownership, owner_party_id, registration_or_serial, default_rate_eur, default_rate_unit, active, created_at, updated_at
        FROM machinery
        ORDER BY updated_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'materials': {
      const result = await sql`
        SELECT id, workspace_id, name, category, default_unit, default_unit_cost_eur, supplier_party_id, active, created_at, updated_at
        FROM materials
        ORDER BY updated_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'invoices': {
      const result = await sql`
        SELECT id, workspace_id, invoice_number, issued_on, due_on, status, subtotal_eur, tax_eur, total_eur, created_at, updated_at
        FROM professional_invoices
        ORDER BY created_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'quotes': {
      const result = await sql`
        SELECT id, workspace_id, quote_number, title, issued_on, valid_until, status, subtotal_eur, tax_eur, total_eur, accepted_at, rejected_at, created_at, updated_at
        FROM professional_quotes
        ORDER BY created_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'plans': {
      const result = await sql`
        SELECT workspace_id, plan_code, status, source, started_at, current_period_end, updated_at
        FROM workspace_plan_subscriptions
        ORDER BY updated_at DESC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'territory': {
      const result = await sql`
        SELECT p.id, p.name, p.slug, p.kind, p.public_enabled, p.is_default_for_municipality,
               m.name AS municipality_name, m.slug AS municipality_slug, m.weather_enabled,
               p.updated_at
        FROM territory_places p
        JOIN territory_municipalities m ON m.id = p.municipality_id
        ORDER BY m.name ASC, p.name ASC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
    case 'market': {
      const result = await sql`
        SELECT source_key, category, period_week, period_start, period_end, price_eur_kg, revision, snapshot_published_on, validated_through, source_name, market_level, status, ingested_at
        FROM market_olive_oil_weekly
        ORDER BY period_end DESC, category ASC
        LIMIT ${limit}
      `.execute(db);
      return result.rows;
    }
  }
}

export function registerAdminOperationsRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/operations', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const metrics = await operationsMetrics(auth.database);
    return {
      generated_at: new Date().toISOString(),
      metrics,
      datasets: datasetCatalog(metrics),
      external_app: await externalAppConfig(auth.database),
    };
  });

  app.get('/api/v1/admin/data/:dataset', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const params = z.object({ dataset: datasetSchema }).safeParse(request.params);
    const query = z.object({ limit: z.coerce.number().int().min(1).max(250).default(50) }).safeParse(request.query);
    if (!params.success || !query.success) return reply.code(400).send({ error: 'validation_error' });
    return {
      dataset: params.data.dataset,
      label: datasetLabels[params.data.dataset],
      rows: await readDataset(auth.database, params.data.dataset, query.data.limit),
      generated_at: new Date().toISOString(),
    };
  });

  app.get('/api/v1/admin/external-app', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    return { config: await externalAppConfig(auth.database) };
  });

  app.put('/api/v1/admin/external-app', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const input = parseBody(externalAppSchema, request.body, reply);
    if (!input) return;
    const now = new Date();
    await sql`
      INSERT INTO site_settings (key, value_json, description, is_public, updated_by, updated_at)
      VALUES (
        'platform.external_app',
        ${JSON.stringify(input)}::jsonb,
        'Configuración privada del lanzador de aplicación externa en Administración.',
        false,
        ${auth.access.userId}::uuid,
        ${now}
      )
      ON CONFLICT (key) DO UPDATE SET
        value_json = EXCLUDED.value_json,
        description = EXCLUDED.description,
        is_public = false,
        updated_by = EXCLUDED.updated_by,
        updated_at = EXCLUDED.updated_at
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'external_app.updated', 'site_setting', 'platform.external_app', {
      enabled: input.enabled,
      mode: input.mode,
      url: input.url,
      health_url: input.health_url ?? null,
    });
    return { config: input, updated_at: now };
  });
}
