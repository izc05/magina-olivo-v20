import { apiFetch } from './api-client';

export type PublicMillReward = {
  id: string;
  businessId: string;
  businessName: string;
  slug: string;
  title: string;
  description: string | null;
  imageUrl: string | null;
  volumeMl: number | null;
  oliveCost: number;
  availableStock: number;
  maxPerUser: number | null;
  startsAt: string | null;
  endsAt: string | null;
};

export type MillRedemption = {
  id: string;
  code: string;
  status: string;
  olivesSpent: number;
  expiresAt: string;
  redeemedAt: string | null;
  createdAt: string;
  productTitle: string;
  businessName: string;
  qrPayload: string | null;
};

export type PublicMill = {
  id: string;
  slug: string;
  title: string;
  summary: string | null;
  body: string | null;
  location: string | null;
  town: string | null;
  phone: string | null;
  address: string | null;
  ctaLabel: string | null;
  mediaUrl: string | null;
  externalUrl: string | null;
  featured: boolean;
  sortOrder: number;
  updatedAt: string;
  publishedAt: string | null;
  millKind: string | null;
  oliveVarieties: string[];
  hasShop: boolean;
  acceptsVisits: boolean;
  rewardCount: number;
};

type AlmazaraPayload = {
  almazaras: Array<{
    id: string;
    slug: string;
    name: string;
    shortDescription: string | null;
    municipalityName: string | null;
    address: string | null;
    phone: string | null;
    website: string | null;
    logoUrl: string | null;
    coverImageUrl: string | null;
    millKind: string | null;
    oliveVarieties: string[];
    hasShop: boolean;
    acceptsVisits: boolean;
    rewardCount: number;
  }>;
};

export async function loadPublicMills(): Promise<PublicMill[]> {
  const payload = await apiFetch<AlmazaraPayload>('/api/v1/public/almazaras');
  return payload.almazaras.map((item, index) => ({
    id: item.id,
    slug: item.slug,
    title: item.name,
    summary: item.shortDescription,
    body: null,
    location: item.municipalityName,
    town: item.municipalityName,
    phone: item.phone,
    address: item.address,
    ctaLabel: item.website ? 'Web oficial' : null,
    mediaUrl: item.coverImageUrl || item.logoUrl,
    externalUrl: item.website,
    featured: false,
    sortOrder: index,
    updatedAt: '',
    publishedAt: null,
    millKind: item.millKind,
    oliveVarieties: item.oliveVarieties ?? [],
    hasShop: item.hasShop,
    acceptsVisits: item.acceptsVisits,
    rewardCount: item.rewardCount,
  }));
}

export async function loadMillRewards(slug: string): Promise<PublicMillReward[]> {
  const payload = await apiFetch<{ rewards: PublicMillReward[] }>(`/api/v1/public/almazaras/${encodeURIComponent(slug)}/rewards`);
  return payload.rewards;
}

export async function redeemMillReward(id: string) {
  return apiFetch<{ redemption: { id: string; code: string; status: string; qrPayload: string; expiresAt: string; productTitle: string; businessName: string; olivesSpent: number } }>(
    `/api/v1/almazara-rewards/${encodeURIComponent(id)}/redeem`,
    { method: 'POST' },
  );
}

export async function loadMyMillRedemptions() {
  return apiFetch<{ redemptions: MillRedemption[] }>('/api/v1/my/almazara-redemptions');
}

export async function cancelMyMillRedemption(id: string) {
  return apiFetch<{ redemption: { id: string; status: 'cancelled'; refunded: boolean } }>(
    `/api/v1/my/almazara-redemptions/${encodeURIComponent(id)}/cancel`,
    { method: 'POST' },
  );
}
