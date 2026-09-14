import { apiFetch } from './api-client';

export type MaginaPassProgram = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  program_type: 'points' | 'stamps' | 'challenge';
  valid_from: string | null;
  valid_until: string | null;
  participating_businesses: number;
  rewards: number;
};

export type MaginaPassStop = {
  business_id: string;
  slug: string;
  name: string;
  short_description: string | null;
  place_name: string | null;
  municipality_name: string | null;
  logo_url: string | null;
  featured_stop: boolean;
  checkin_points: number;
};

export type MaginaPassReward = {
  id: string;
  business_id: string | null;
  business_name: string | null;
  title: string;
  description: string | null;
  points_cost: number;
  reward_type: 'benefit' | 'discount' | 'gift' | 'experience' | 'offer';
  redemption_instructions: string | null;
  valid_from: string | null;
  valid_until: string | null;
};

export type MaginaPassWallet = {
  id: string;
  program_id: string;
  program_slug: string;
  program_name: string;
  points_balance: number;
  total_points_earned: number;
  total_checkins: number;
  updated_at: string;
};

export type MaginaPassState = {
  wallets: MaginaPassWallet[];
  checkins: Array<{
    id: string;
    program_id: string;
    business_name: string | null;
    source_type: 'business_qr' | 'route' | 'event' | 'admin';
    points_awarded: number;
    occurred_at: string;
  }>;
  redemptions: Array<{
    id: string;
    program_id: string;
    reward_title: string;
    business_name: string | null;
    points_spent: number;
    status: 'issued' | 'redeemed' | 'cancelled' | 'expired';
    issued_at: string;
    redeemed_at: string | null;
    expires_at: string | null;
  }>;
};

export const businessPassApi = {
  programs: () => apiFetch<{ programs: MaginaPassProgram[] }>('/api/v1/public/magina-pass/programs'),
  program: (slug: string) => apiFetch<{ program: MaginaPassProgram; stops: MaginaPassStop[]; rewards: MaginaPassReward[] }>(`/api/v1/public/magina-pass/programs/${encodeURIComponent(slug)}`),
  myPass: () => apiFetch<MaginaPassState>('/api/v1/my/magina-pass'),
  checkin: (code: string) => apiFetch<{
    checkin: { businessId: string; businessName: string; pointsAwarded: number };
    wallet: { points_balance: number; total_points_earned: number; total_checkins: number };
  }>('/api/v1/my/magina-pass/checkins', { method: 'POST', body: JSON.stringify({ code }) }),
  redeem: (rewardId: string) => apiFetch<{
    redemption: { id: string; rewardTitle: string; code: string; pointsSpent: number; pointsBalance: number };
    warning: string;
  }>(`/api/v1/my/magina-pass/rewards/${encodeURIComponent(rewardId)}/redeem`, { method: 'POST' }),
};
