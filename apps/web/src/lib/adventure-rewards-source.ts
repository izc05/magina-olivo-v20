import { apiFetch } from './api-client';

export type AdventureReward = {
  sponsorship_id: string;
  route_id: string;
  route_slug: string;
  route_name: string;
  adventure_completed_at: string;
  sponsor_name: string;
  sponsor_logo_url: string | null;
  sponsor_url: string | null;
  headline: string | null;
  description: string | null;
  cta_label: string | null;
  cta_url: string | null;
  promo_code: string | null;
  disclosure: string;
  ends_at: string | null;
};

export type AdventureRewardsResponse = {
  rewards: AdventureReward[];
  notice: string;
};

export function loadAdventureRewards() {
  return apiFetch<AdventureRewardsResponse>('/api/v1/adventure/rewards');
}

export function trackAdventureRewardEvent(
  sponsorshipId: string,
  eventType: 'impression' | 'click',
  sessionKey: string | null,
) {
  return apiFetch<{ accepted: true }>(`/api/v1/public/route-sponsorships/${encodeURIComponent(sponsorshipId)}/events`, {
    method: 'POST',
    keepalive: true,
    body: JSON.stringify({
      event_type: eventType,
      session_key: sessionKey,
      metadata: { surface: 'adventure_reward' },
    }),
  });
}
