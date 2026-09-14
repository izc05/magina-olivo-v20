import { apiFetch } from './api-client';

export type BusinessCategory = {
  id: string;
  parent_id: string | null;
  slug: string;
  name: string;
  description: string | null;
  icon_key: string | null;
  business_count: number;
};

export type BusinessCategoryRef = {
  slug: string;
  name: string;
  primary: boolean;
};

export type BusinessDirectoryItem = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string | null;
  territory: {
    municipalityId: string | null;
    municipalityName: string | null;
    municipalitySlug: string | null;
    placeId: string | null;
    placeName: string | null;
    placeSlug: string | null;
  };
  address: string | null;
  location: { longitude: number; latitude: number } | null;
  distanceKm: number | null;
  categories: BusinessCategoryRef[];
  verificationStatus: 'unverified' | 'pending' | 'verified' | 'rejected';
  verified: boolean;
  placement: {
    label: 'Patrocinado' | 'Destacado' | null;
    sponsored: boolean;
    featured: boolean;
  };
  logoUrl: string | null;
  coverImageUrl: string | null;
};

export type BusinessMedia = {
  id: string;
  kind: 'logo' | 'cover' | 'photo' | 'video' | 'document';
  url: string;
  thumbnailUrl: string | null;
  altText: string | null;
  credit: string | null;
  origin: 'owned' | 'official' | 'licensed' | 'external_reference' | 'ai_generated';
  sourceUrl: string | null;
  aiGenerated: boolean;
  aiDisclosure: string | null;
};

export type BusinessDetail = BusinessDirectoryItem & {
  legalName: string | null;
  description: string | null;
  contact: {
    phone: string | null;
    whatsapp: string | null;
    email: string | null;
    website: string | null;
    socialLinks: Record<string, unknown>;
  };
  openingHours: Record<string, unknown>;
  media: BusinessMedia[];
  sources: Array<{
    sourceName: string;
    sourceUrl: string | null;
    fetchedAt: string | null;
    lastSyncedAt: string | null;
    syncStatus: string;
  }>;
  publishedAt: string | null;
  updatedAt: string;
};

export type BusinessOffer = {
  id: string;
  slug: string;
  title: string;
  summary: string | null;
  description: string | null;
  offerType: 'promotion' | 'discount' | 'fixed_price' | 'bundle' | 'gift' | 'experience' | 'seasonal';
  pricing: {
    originalPriceCents: number | null;
    offerPriceCents: number | null;
    currency: string;
  };
  promoCode: string | null;
  redemption: {
    mode: 'contact' | 'request' | 'external_link' | 'show_code';
    url: string | null;
  };
  terms: string | null;
  validFrom: string | null;
  validUntil: string | null;
  maxRedemptions: number | null;
};

export type BusinessDirectoryFilters = {
  q?: string;
  municipalityId?: string;
  placeId?: string;
  category?: string;
  featured?: boolean;
  sponsored?: boolean;
  lat?: number;
  lng?: number;
  radiusKm?: number;
  limit?: number;
  offset?: number;
};

export type BusinessEventType =
  | 'directory_impression'
  | 'profile_view'
  | 'phone_click'
  | 'whatsapp_click'
  | 'email_click'
  | 'website_click'
  | 'directions_click'
  | 'offer_view'
  | 'offer_redeem';

function queryString(filters: BusinessDirectoryFilters) {
  const params = new URLSearchParams();
  if (filters.q?.trim()) params.set('q', filters.q.trim());
  if (filters.municipalityId) params.set('municipalityId', filters.municipalityId);
  if (filters.placeId) params.set('placeId', filters.placeId);
  if (filters.category) params.set('category', filters.category);
  if (filters.featured !== undefined) params.set('featured', String(filters.featured));
  if (filters.sponsored !== undefined) params.set('sponsored', String(filters.sponsored));
  if (filters.lat !== undefined) params.set('lat', String(filters.lat));
  if (filters.lng !== undefined) params.set('lng', String(filters.lng));
  if (filters.radiusKm !== undefined) params.set('radiusKm', String(filters.radiusKm));
  if (filters.limit !== undefined) params.set('limit', String(filters.limit));
  if (filters.offset !== undefined) params.set('offset', String(filters.offset));
  return params.toString();
}

export async function loadBusinessCategories() {
  const payload = await apiFetch<{ categories: BusinessCategory[] }>('/api/v1/public/business-categories');
  return payload.categories;
}

export async function loadBusinesses(filters: BusinessDirectoryFilters = {}) {
  const query = queryString(filters);
  return apiFetch<{
    businesses: BusinessDirectoryItem[];
    meta: { limit: number; offset: number; count: number; sponsoredDisclosure: string };
  }>(`/api/v1/public/businesses${query ? `?${query}` : ''}`);
}

export async function loadBusiness(slug: string) {
  const payload = await apiFetch<{ business: BusinessDetail }>(`/api/v1/public/businesses/${encodeURIComponent(slug)}`);
  return payload.business;
}

export async function loadBusinessOffers(slug: string) {
  const payload = await apiFetch<{ offers: BusinessOffer[] }>(`/api/v1/public/businesses/${encodeURIComponent(slug)}/offers`);
  return payload.offers;
}

export async function trackBusinessEvent(slug: string, input: {
  eventType: BusinessEventType;
  offerId?: string;
  anonymousId?: string;
  sourceContext?: string;
  sourceKey?: string;
  metadata?: Record<string, unknown>;
}) {
  return apiFetch<{ accepted: boolean }>(`/api/v1/public/businesses/${encodeURIComponent(slug)}/events`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export async function submitBusinessLead(slug: string, input: {
  kind: 'contact' | 'quote' | 'booking' | 'availability' | 'order';
  contactName: string;
  contactEmail?: string;
  contactPhone?: string;
  message?: string;
  requestedFor?: string | null;
  partySize?: number | null;
  offerId?: string;
  anonymousId?: string;
  sourceContext?: string;
  sourceKey?: string;
  consentBusinessContact: true;
}) {
  return apiFetch<{ lead: { id: string; status: string; createdAt: string }; message: string }>(
    `/api/v1/public/businesses/${encodeURIComponent(slug)}/leads`,
    { method: 'POST', body: JSON.stringify(input) },
  );
}

export async function submitBusinessClaim(slug: string, input: {
  claimantName: string;
  claimantEmail: string;
  claimantPhone?: string;
  relationship: string;
  message?: string;
  evidenceUrls?: string[];
}) {
  return apiFetch<{ claim: { id: string; status: string; createdAt: string }; message: string }>(
    `/api/v1/public/businesses/${encodeURIComponent(slug)}/claims`,
    { method: 'POST', body: JSON.stringify(input) },
  );
}
