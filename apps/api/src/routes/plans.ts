import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

const interestSchema = z.object({ target_plan: z.enum(['pro', 'professional']) });
type PlanCode = 'free' | 'pro' | 'professional';
type FutureEntitlement = 'advanced_automation' | 'advanced_analysis' | 'professional_commercial_suite';

const planCatalog = [
  {
    code: 'free',
    name: 'Campo',
    commercial_state: 'available',
    price_label: 'Gratis',
    chargeable: false,
    checkout_available: false,
    future_entitlements: [] as FutureEntitlement[],
    summary: 'El cuaderno digital de tu olivar durante la Beta.',
    highlights: [
      'Mi Campo, campañas y registro agrícola',
      'Clima, alertas y documentación disponibles en la Beta',
      'Sin tarjeta ni renovación automática',
    ],
  },
  {
    code: 'pro',
    name: 'Pro',
    commercial_state: 'coming_soon',
    price_label: 'Precio por definir',
    chargeable: false,
    checkout_available: false,
    future_entitlements: ['advanced_automation', 'advanced_analysis'] as FutureEntitlement[],
    summary: 'Para quien quiera más automatización y análisis cuando termine la Beta.',
    highlights: [
      'Todo lo existente en Campo durante la Beta',
      'Preparado para futuras automatizaciones y análisis avanzados',
      'Puedes registrar interés sin realizar ningún pago',
    ],
  },
  {
    code: 'professional',
    name: 'Profesional',
    commercial_state: 'coming_soon',
    price_label: 'Precio por definir',
    chargeable: false,
    checkout_available: false,
    future_entitlements: ['advanced_automation', 'advanced_analysis', 'professional_commercial_suite'] as FutureEntitlement[],
    summary: 'Para trabajos a terceros, clientes y actividad profesional agrícola.',
    highlights: [
      'Herramientas profesionales existentes siguen abiertas en la Beta',
      'Preparado para el futuro empaquetado comercial del módulo Profesional',
      'Puedes registrar interés sin activar una suscripción',
    ],
  },
] as const;

type SubscriptionRow = {
  plan_code: PlanCode;
  status: 'active' | 'trialing' | 'paused' | 'cancelled';
  source: 'internal' | 'manual' | 'billing';
  started_at: Date | string;
  current_period_end: Date | string | null;
};

type InterestRow = {
  id: string;
  target_plan: 'pro' | 'professional';
  status: 'pending' | 'contacted' | 'converted' | 'cancelled';
  created_at: Date | string;
  updated_at: Date | string;
};

function canManagePlan(role: string) {
  return role === 'owner' || role === 'admin' || role === 'development';
}

function entitlementsFor(plan: PlanCode): FutureEntitlement[] {
  return [...(planCatalog.find((entry) => entry.code === plan)?.future_entitlements ?? [])];
}

export function registerPlanRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/plans', async () => ({
    schema_version: 1,
    beta_policy: 'all_existing_beta_features_remain_available',
    beta_access_override: true,
    billing_enabled: false,
    checkout_available: false,
    plans: planCatalog,
  }));

  app.get('/api/v1/plans/current', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const subscriptionResult = await sql<SubscriptionRow>`
      SELECT plan_code, status, source, started_at, current_period_end
      FROM workspace_plan_subscriptions
      WHERE workspace_id = ${context.workspaceId}::uuid
      LIMIT 1
    `.execute(database);
    const subscription = subscriptionResult.rows[0] ?? null;
    const effectivePlan: PlanCode = subscription && (subscription.status === 'active' || subscription.status === 'trialing')
      ? subscription.plan_code
      : 'free';

    const interestResult = await sql<InterestRow>`
      SELECT id::text, target_plan, status, created_at, updated_at
      FROM plan_interest_requests
      WHERE workspace_id = ${context.workspaceId}::uuid
        AND status IN ('pending', 'contacted')
        AND target_plan <> ${effectivePlan}
      ORDER BY updated_at DESC
    `.execute(database);

    return {
      workspace_id: context.workspaceId,
      effective_plan: effectivePlan,
      future_entitlements: entitlementsFor(effectivePlan),
      beta_access_override: true,
      subscription: subscription
        ? {
            plan_code: subscription.plan_code,
            status: subscription.status,
            source: subscription.source,
            started_at: subscription.started_at,
            current_period_end: subscription.current_period_end,
          }
        : {
            plan_code: 'free',
            status: 'active',
            source: 'default',
            started_at: null,
            current_period_end: null,
          },
      can_manage_plan: canManagePlan(context.role),
      billing_enabled: false,
      checkout_available: false,
      beta_policy: 'all_existing_beta_features_remain_available',
      interests: interestResult.rows,
    };
  });

  app.post('/api/v1/plans/interest', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    if (!canManagePlan(context.role)) {
      return reply.code(403).send({
        error: 'plan_management_forbidden',
        message: 'Only workspace owners or admins can register commercial plan interest.',
      });
    }

    const input = parseBody(interestSchema, request.body, reply);
    if (!input) return;

    const subscriptionResult = await sql<SubscriptionRow>`
      SELECT plan_code, status, source, started_at, current_period_end
      FROM workspace_plan_subscriptions
      WHERE workspace_id = ${context.workspaceId}::uuid
      LIMIT 1
    `.execute(database);
    const subscription = subscriptionResult.rows[0] ?? null;
    if (subscription && (subscription.status === 'active' || subscription.status === 'trialing') && subscription.plan_code === input.target_plan) {
      return reply.code(409).send({
        error: 'plan_already_active',
        message: 'That plan is already active for this workspace.',
      });
    }

    const previous = await sql<{ id: string; status: string }>`
      SELECT id::text, status
      FROM plan_interest_requests
      WHERE workspace_id = ${context.workspaceId}::uuid
        AND target_plan = ${input.target_plan}
      LIMIT 1
    `.execute(database);

    const result = await sql<InterestRow>`
      INSERT INTO plan_interest_requests (workspace_id, requested_by, target_plan, status)
      VALUES (${context.workspaceId}::uuid, ${context.userId}::uuid, ${input.target_plan}, 'pending')
      ON CONFLICT (workspace_id, target_plan)
      DO UPDATE SET
        requested_by = EXCLUDED.requested_by,
        status = 'pending',
        updated_at = now()
      RETURNING id::text, target_plan, status, created_at, updated_at
    `.execute(database);

    reply.code(previous.rows[0] ? 200 : 201);
    return {
      interest: result.rows[0],
      already_registered: Boolean(previous.rows[0] && previous.rows[0].status === 'pending'),
      billing_enabled: false,
      checkout_available: false,
      message: 'Interest registered. No payment or subscription has been created.',
    };
  });
}
