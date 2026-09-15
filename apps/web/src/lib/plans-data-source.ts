'use client';

import { apiFetch } from './api-client';

export type PlanCode = 'free' | 'pro' | 'professional';
export type FutureEntitlement = 'advanced_automation' | 'advanced_analysis' | 'professional_commercial_suite';
export type PlanCatalogEntry = {
  code: PlanCode;
  name: string;
  commercial_state: 'available' | 'coming_soon';
  price_label: string;
  chargeable: boolean;
  checkout_available: boolean;
  future_entitlements: FutureEntitlement[];
  summary: string;
  highlights: string[];
};

export type PlanCatalogPayload = {
  schema_version: number;
  beta_policy: 'all_existing_beta_features_remain_available';
  beta_access_override: true;
  billing_enabled: boolean;
  checkout_available: boolean;
  plans: PlanCatalogEntry[];
};

export type CurrentPlanPayload = {
  workspace_id: string;
  effective_plan: PlanCode;
  future_entitlements: FutureEntitlement[];
  beta_access_override: true;
  subscription: {
    plan_code: PlanCode;
    status: 'active' | 'trialing' | 'paused' | 'cancelled';
    source: 'default' | 'internal' | 'manual' | 'billing';
    started_at: string | null;
    current_period_end: string | null;
  };
  can_manage_plan: boolean;
  billing_enabled: boolean;
  checkout_available: boolean;
  beta_policy: 'all_existing_beta_features_remain_available';
  interests: Array<{
    id: string;
    target_plan: 'pro' | 'professional';
    status: 'pending' | 'contacted' | 'converted' | 'cancelled';
    created_at: string;
    updated_at: string;
  }>;
};

export const fallbackPlanCatalog: PlanCatalogPayload = {
  schema_version: 1,
  beta_policy: 'all_existing_beta_features_remain_available',
  beta_access_override: true,
  billing_enabled: false,
  checkout_available: false,
  plans: [
    {
      code: 'free',
      name: 'Campo',
      commercial_state: 'available',
      price_label: 'Gratis',
      chargeable: false,
      checkout_available: false,
      future_entitlements: [],
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
      future_entitlements: ['advanced_automation', 'advanced_analysis'],
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
      future_entitlements: ['advanced_automation', 'advanced_analysis', 'professional_commercial_suite'],
      summary: 'Para trabajos a terceros, clientes y actividad profesional agrícola.',
      highlights: [
        'Herramientas profesionales existentes siguen abiertas en la Beta',
        'Preparado para el futuro empaquetado comercial del módulo Profesional',
        'Puedes registrar interés sin activar una suscripción',
      ],
    },
  ],
};

export async function loadPlanCatalog() {
  try {
    return await apiFetch<PlanCatalogPayload>('/api/v1/public/plans');
  } catch {
    return fallbackPlanCatalog;
  }
}

export function loadCurrentPlan(workspaceId: string) {
  return apiFetch<CurrentPlanPayload>('/api/v1/plans/current', { workspaceId });
}

export function registerPlanInterest(workspaceId: string, targetPlan: 'pro' | 'professional') {
  return apiFetch<{
    interest: CurrentPlanPayload['interests'][number];
    already_registered: boolean;
    billing_enabled: false;
    checkout_available: false;
    message: string;
  }>('/api/v1/plans/interest', {
    method: 'POST',
    workspaceId,
    body: JSON.stringify({ target_plan: targetPlan }),
  });
}
