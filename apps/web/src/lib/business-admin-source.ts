import { apiFetch } from './api-client';

export type AdminBusinessCategory = {
  id: string;
  parent_id: string | null;
  slug: string;
  name: string;
  description: string | null;
  icon_key: string | null;
  sort_order: number;
  active: boolean;
  business_count: number;
};

export type AdminBusiness = {
  id: string;
  slug: string;
  name: string;
  legalName: string | null;
  shortDescription: string | null;
  description: string | null;
  municipalityId: string | null;
  municipalityName: string | null;
  placeId: string | null;
  placeName: string | null;
  address: string | null;
  location: { longitude: number; latitude: number } | null;
  phone: string | null;
  whatsapp: string | null;
  email: string | null;
  website: string | null;
  socialLinks: Record<string, unknown>;
  openingHours: Record<string, unknown>;
  logoUrl: string | null;
  coverImageUrl: string | null;
  verificationStatus: 'unverified' | 'pending' | 'verified' | 'rejected';
  status: 'draft' | 'published' | 'archived';
  commercialPlan: 'free' | 'featured' | 'premium' | 'sponsor';
  featured: boolean;
  sponsored: boolean;
  priority: number;
  campaignStart: string | null;
  campaignEnd: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  categories: Array<{ slug: string; name: string; primary: boolean }>;
  mediaCount: number;
  sourceCount: number;
  pendingClaimCount: number;
};

export type AdminBusinessClaim = {
  id: string;
  business_id: string;
  business_name: string;
  claimant_name: string;
  claimant_email: string;
  claimant_phone: string | null;
  evidence: unknown;
  status: 'pending' | 'needs_info' | 'approved' | 'rejected' | 'cancelled';
  admin_notes: string | null;
  reviewed_at: string | null;
  created_at: string;
  updated_at: string;
};

export type BusinessAdminCatalog = {
  businesses: AdminBusiness[];
  categories: AdminBusinessCategory[];
  claims: AdminBusinessClaim[];
  stats: {
    total: number;
    published: number;
    draft: number;
    verified: number;
    sponsored: number;
    pendingClaims: number;
  };
};

export type TerritoryCatalog = {
  municipalities: Array<{ id: string; name: string; slug: string; active: boolean }>;
  places: Array<{ id: string; name: string; municipality_id: string; municipality_name: string; public_enabled: boolean }>;
};

export type BusinessWriteInput = {
  slug?: string;
  name?: string;
  legalName?: string | null;
  shortDescription?: string | null;
  description?: string | null;
  municipalityId?: string | null;
  placeId?: string | null;
  address?: string | null;
  location?: { latitude: number; longitude: number } | null;
  phone?: string | null;
  whatsapp?: string | null;
  email?: string | null;
  website?: string | null;
  logoUrl?: string | null;
  coverImageUrl?: string | null;
  status?: 'draft' | 'published' | 'archived';
  verificationStatus?: 'unverified' | 'pending' | 'verified' | 'rejected';
  commercialPlan?: 'free' | 'featured' | 'premium' | 'sponsor';
  featured?: boolean;
  sponsored?: boolean;
  priority?: number;
  campaignStart?: string | null;
  campaignEnd?: string | null;
  categorySlugs?: string[];
  primaryCategorySlug?: string | null;
};

export type BusinessCategoryWriteInput = {
  slug?: string;
  name?: string;
  description?: string | null;
  iconKey?: string | null;
  parentId?: string | null;
  sortOrder?: number;
  active?: boolean;
};

export const businessAdminApi = {
  catalog: () => apiFetch<BusinessAdminCatalog>('/api/v1/admin/business-directory'),
  territory: () => apiFetch<TerritoryCatalog>('/api/v1/admin/territory/catalog'),
  create: (input: Required<Pick<BusinessWriteInput, 'slug' | 'name'>> & BusinessWriteInput) =>
    apiFetch<{ business: AdminBusiness }>('/api/v1/admin/businesses', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: string, input: BusinessWriteInput) =>
    apiFetch<{ business: AdminBusiness }>(`/api/v1/admin/businesses/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(input) }),
  reviewClaim: (id: string, input: { status: 'needs_info' | 'approved' | 'rejected' | 'cancelled'; adminNotes?: string | null }) =>
    apiFetch<{ ok: true; claimId: string; status: string }>(`/api/v1/admin/business-claims/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(input) }),
  addMedia: (businessId: string, input: {
    kind: 'logo' | 'cover' | 'photo' | 'video' | 'document';
    url: string;
    thumbnailUrl?: string | null;
    altText?: string | null;
    credit?: string | null;
    origin?: 'owned' | 'official' | 'licensed' | 'external_reference' | 'ai_generated';
    sourceUrl?: string | null;
    aiGenerated?: boolean;
    aiDisclosure?: string | null;
    sortOrder?: number;
  }) => apiFetch<{ media: { id: string } }>(`/api/v1/admin/businesses/${encodeURIComponent(businessId)}/media`, { method: 'POST', body: JSON.stringify(input) }),
  addSource: (businessId: string, input: {
    sourceName: string;
    externalId?: string | null;
    sourceUrl?: string | null;
    licenseNotes?: string | null;
    rawData?: Record<string, unknown>;
    fetchedAt?: string | null;
  }) => apiFetch<{ source: { id: string } }>(`/api/v1/admin/businesses/${encodeURIComponent(businessId)}/sources`, { method: 'POST', body: JSON.stringify(input) }),
  createCategory: (input: Required<Pick<BusinessCategoryWriteInput, 'slug' | 'name'>> & BusinessCategoryWriteInput) =>
    apiFetch<{ category: { id: string } & BusinessCategoryWriteInput }>('/api/v1/admin/business-categories', { method: 'POST', body: JSON.stringify(input) }),
  updateCategory: (id: string, input: BusinessCategoryWriteInput) =>
    apiFetch<{ ok: true; categoryId: string }>(`/api/v1/admin/business-categories/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(input) }),
};
