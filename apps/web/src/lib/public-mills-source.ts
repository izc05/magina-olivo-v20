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
  requiredLevel: number;
  requiredLevelName: string;
  minXp: number;
};

export type MillRewardUnlockState = {
  xp: number;
  balance: number;
  currentLevel: number;
  currentLevelName: string;
  rewards: Array<{
    rewardId: string;
    requiredLevel: number;
    requiredLevelName: string;
    minXp: number;
    unlocked: boolean;
  }>;
};

export type MillRedemption = {
  id: string;
  code: string | null;
  status: string;
  olivesSpent: number;
  expiresAt: string;
  redeemedAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  productTitle: string;
  businessName: string;
  businessSlug: string | null;
  pickupAddress: string | null;
  pickupMunicipalityName: string | null;
  pickupPhone: string | null;
  pickupLongitude: number | null;
  pickupLatitude: number | null;
  qrPayload: string | null;
  qrReady: boolean;
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

type RewardUnlockPayload = {
  rewards: Array<{
    rewardId: string;
    requiredLevel: number;
    requiredLevelName: string;
    minXp: number;
  }>;
};

type RedemptionPickupPayload = {
  pickups: Array<{
    redemptionId: string;
    businessSlug: string;
    address: string | null;
    municipalityName: string | null;
    phone: string | null;
    longitude: number | null;
    latitude: number | null;
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
  const [payload, unlockPayload] = await Promise.all([
    apiFetch<{ rewards: Omit<PublicMillReward, 'requiredLevel' | 'requiredLevelName' | 'minXp'>[] }>(`/api/v1/public/almazaras/${encodeURIComponent(slug)}/rewards`),
    apiFetch<RewardUnlockPayload>(`/api/v1/public/almazaras/${encodeURIComponent(slug)}/reward-unlocks`),
  ]);
  const unlocks = new Map(unlockPayload.rewards.map((item) => [item.rewardId, item]));
  return payload.rewards.map((reward) => {
    const unlock = unlocks.get(reward.id);
    return {
      ...reward,
      requiredLevel: unlock?.requiredLevel ?? 1,
      requiredLevelName: unlock?.requiredLevelName ?? 'Brote',
      minXp: unlock?.minXp ?? 0,
    };
  });
}

export async function loadMyMillRewardUnlocks(slug: string): Promise<MillRewardUnlockState> {
  return apiFetch<MillRewardUnlockState>(`/api/v1/my/almazaras/${encodeURIComponent(slug)}/reward-unlocks`);
}

export async function redeemMillReward(id: string) {
  return apiFetch<{ redemption: { id: string; token: string; status: string; qrPayload: string; expiresAt: string; productTitle: string; businessName: string; olivesSpent: number } }>(
    `/api/v1/almazara-rewards/${encodeURIComponent(id)}/redeem`,
    { method: 'POST' },
  );
}

export async function loadMyMillRedemptions() {
  const [payload, pickupPayload] = await Promise.all([
    apiFetch<{ redemptions: Omit<MillRedemption, 'businessSlug' | 'pickupAddress' | 'pickupMunicipalityName' | 'pickupPhone' | 'pickupLongitude' | 'pickupLatitude'>[] }>('/api/v1/my/almazara-redemptions'),
    apiFetch<RedemptionPickupPayload>('/api/v1/my/almazara-redemption-pickups'),
  ]);
  const pickupByRedemption = new Map(pickupPayload.pickups.map((item) => [item.redemptionId, item]));
  return {
    redemptions: payload.redemptions.map((item) => {
      const pickup = pickupByRedemption.get(item.id);
      return {
        ...item,
        businessSlug: pickup?.businessSlug ?? null,
        pickupAddress: pickup?.address ?? null,
        pickupMunicipalityName: pickup?.municipalityName ?? null,
        pickupPhone: pickup?.phone ?? null,
        pickupLongitude: pickup?.longitude ?? null,
        pickupLatitude: pickup?.latitude ?? null,
      };
    }),
  };
}

export async function cancelMyMillRedemption(id: string) {
  return apiFetch<{ redemption: { id: string; status: 'cancelled'; refunded: boolean } }>(
    `/api/v1/my/almazara-redemptions/${encodeURIComponent(id)}/cancel`,
    { method: 'POST' },
  );
}
