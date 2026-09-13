import { apiFetch } from './api-client';

export type BusinessRevenueMetric = {
  id: string;
  slug: string;
  name: string;
  commercialPlan: string;
  profileViews: number;
  contactClicks: number;
  leads: number;
  wonLeads: number;
  wonValueCents: number;
  activeOffers: number;
};

export type BusinessLeadAdmin = {
  id: string;
  business_id: string;
  business_name: string;
  offer_id: string | null;
  offer_title: string | null;
  kind: 'contact' | 'quote' | 'booking' | 'availability' | 'order';
  contact_name: string;
  contact_email: string | null;
  contact_phone: string | null;
  message: string | null;
  requested_for: string | null;
  party_size: number | null;
  source_context: string | null;
  source_key: string | null;
  status: 'new' | 'contacted' | 'qualified' | 'won' | 'lost' | 'spam';
  estimated_value_cents: number | null;
  actual_value_cents: number | null;
  currency: string;
  created_at: string;
  updated_at: string;
};

export type BusinessOfferAdmin = {
  id: string;
  business_id: string;
  business_name: string;
  slug: string;
  title: string;
  summary: string | null;
  description: string | null;
  offer_type: string;
  original_price_cents: number | null;
  offer_price_cents: number | null;
  currency: string;
  promo_code: string | null;
  redemption_mode: 'contact' | 'request' | 'external_link' | 'show_code';
  redemption_url: string | null;
  terms: string | null;
  valid_from: string | null;
  valid_until: string | null;
  max_redemptions: number | null;
  status: 'draft' | 'published' | 'archived';
  sort_order: number;
  published_at: string | null;
  created_at: string;
  updated_at: string;
};

export type BusinessRevenueDashboard = {
  periodDays: number;
  since: string;
  businesses: BusinessRevenueMetric[];
  leads: BusinessLeadAdmin[];
  offers: BusinessOfferAdmin[];
};

export type BusinessOfferWriteInput = {
  slug: string;
  title: string;
  summary?: string | null;
  description?: string | null;
  offerType?: 'promotion' | 'discount' | 'fixed_price' | 'bundle' | 'gift' | 'experience' | 'seasonal';
  originalPriceCents?: number | null;
  offerPriceCents?: number | null;
  currency?: string;
  promoCode?: string | null;
  redemptionMode?: 'contact' | 'request' | 'external_link' | 'show_code';
  redemptionUrl?: string | null;
  terms?: string | null;
  validFrom?: string | null;
  validUntil?: string | null;
  maxRedemptions?: number | null;
  status?: 'draft' | 'published' | 'archived';
  sortOrder?: number;
};

export const businessRevenueAdminApi = {
  dashboard: (filters: { businessId?: string; days?: number } = {}) => {
    const params = new URLSearchParams();
    if (filters.businessId) params.set('businessId', filters.businessId);
    if (filters.days) params.set('days', String(filters.days));
    const query = params.toString();
    return apiFetch<BusinessRevenueDashboard>(`/api/v1/admin/business-revenue${query ? `?${query}` : ''}`);
  },
  createOffer: (businessId: string, input: BusinessOfferWriteInput) =>
    apiFetch<{ offer: { id: string } }>(`/api/v1/admin/businesses/${encodeURIComponent(businessId)}/offers`, {
      method: 'POST', body: JSON.stringify(input),
    }),
  updateOffer: (id: string, input: Partial<BusinessOfferWriteInput>) =>
    apiFetch<{ ok: true; offerId: string }>(`/api/v1/admin/business-offers/${encodeURIComponent(id)}`, {
      method: 'PATCH', body: JSON.stringify(input),
    }),
  updateLead: (id: string, input: {
    status: BusinessLeadAdmin['status'];
    estimatedValueCents?: number | null;
    actualValueCents?: number | null;
  }) => apiFetch<{ ok: true; leadId: string; status: string }>(`/api/v1/admin/business-leads/${encodeURIComponent(id)}`, {
    method: 'PATCH', body: JSON.stringify(input),
  }),
};
