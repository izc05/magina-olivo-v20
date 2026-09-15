import { apiFetch } from './api-client';

export type MaginaPassAdminProgram = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  program_type: 'points' | 'stamps' | 'challenge';
  status: 'draft' | 'published' | 'archived';
  valid_from: string | null;
  valid_until: string | null;
  default_checkin_points: number;
  default_cooldown_hours: number;
  business_count: number;
  wallet_count: number;
  checkin_count: number;
  redemption_count: number;
};

export type MaginaPassAdminStop = {
  program_id: string;
  business_id: string;
  business_name: string;
  business_slug: string;
  checkin_points: number | null;
  cooldown_hours: number | null;
  featured_stop: boolean;
  active: boolean;
  sort_order: number;
  active_qr_count: number;
};

export type MaginaPassAdminReward = {
  id: string;
  program_id: string;
  business_id: string | null;
  business_name: string | null;
  title: string;
  description: string | null;
  points_cost: number;
  reward_type: 'benefit' | 'discount' | 'gift' | 'experience' | 'offer';
  redemption_instructions: string | null;
  max_redemptions: number | null;
  valid_from: string | null;
  valid_until: string | null;
  status: 'draft' | 'published' | 'archived';
  redemption_count: number;
};

export type MaginaPassAdminCatalog = {
  programs: MaginaPassAdminProgram[];
  stops: MaginaPassAdminStop[];
  rewards: MaginaPassAdminReward[];
};

export const businessPassAdminApi = {
  catalog: () => apiFetch<MaginaPassAdminCatalog>('/api/v1/admin/magina-pass'),
  createProgram: (input: {
    slug: string;
    name: string;
    description?: string | null;
    programType?: 'points' | 'stamps' | 'challenge';
    defaultCheckinPoints?: number;
    defaultCooldownHours?: number;
    validFrom?: string | null;
    validUntil?: string | null;
    status?: 'draft' | 'published' | 'archived';
  }) => apiFetch<{ program: { id: string } }>('/api/v1/admin/magina-pass/programs', { method: 'POST', body: JSON.stringify(input) }),
  upsertStop: (programId: string, input: {
    businessId: string;
    checkinPoints?: number | null;
    cooldownHours?: number | null;
    featuredStop?: boolean;
    active?: boolean;
    sortOrder?: number;
  }) => apiFetch<{ ok: true }>(`/api/v1/admin/magina-pass/programs/${encodeURIComponent(programId)}/businesses`, { method: 'POST', body: JSON.stringify(input) }),
  createQr: (programId: string, businessId: string, input: { label?: string | null; validUntil?: string | null } = {}) =>
    apiFetch<{ qr: { id: string; code: string; payload: string }; warning: string }>(`/api/v1/admin/magina-pass/programs/${encodeURIComponent(programId)}/businesses/${encodeURIComponent(businessId)}/qr`, { method: 'POST', body: JSON.stringify(input) }),
  createReward: (programId: string, input: {
    businessId?: string | null;
    title: string;
    description?: string | null;
    pointsCost: number;
    rewardType?: 'benefit' | 'discount' | 'gift' | 'experience' | 'offer';
    redemptionInstructions?: string | null;
    maxRedemptions?: number | null;
    validFrom?: string | null;
    validUntil?: string | null;
    status?: 'draft' | 'published' | 'archived';
  }) => apiFetch<{ reward: { id: string } }>(`/api/v1/admin/magina-pass/programs/${encodeURIComponent(programId)}/rewards`, { method: 'POST', body: JSON.stringify(input) }),
};
