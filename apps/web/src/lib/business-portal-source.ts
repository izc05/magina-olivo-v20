import { apiFetch } from './api-client';

export type MyBusiness = {
  id: string;
  slug: string;
  name: string;
  short_description: string | null;
  status: string;
  verification_status: string;
  commercial_plan: string;
  role: 'owner' | 'manager' | 'editor' | 'analyst';
  municipality_name: string | null;
  place_name: string | null;
  logo_url: string | null;
};

export type MyBusinessDashboard = {
  role: 'owner' | 'manager' | 'editor' | 'analyst';
  periodDays: number;
  business: {
    id: string;
    slug: string;
    name: string;
    shortDescription: string | null;
    description: string | null;
    address: string | null;
    phone: string | null;
    whatsapp: string | null;
    email: string | null;
    website: string | null;
    socialLinks: Record<string, unknown>;
    openingHours: Record<string, unknown>;
    logoUrl: string | null;
    coverImageUrl: string | null;
    commercialPlan: string;
    verificationStatus: string;
    status: string;
  };
  metrics: {
    profileViews: number;
    contactClicks: number;
    leads: number;
    wonLeads: number;
    wonValueCents: number;
  };
  leads: Array<{
    id: string;
    kind: string;
    contact_name: string;
    contact_email: string | null;
    contact_phone: string | null;
    message: string | null;
    requested_for: string | null;
    party_size: number | null;
    status: string;
    actual_value_cents: number | null;
    currency: string;
    created_at: string;
    offer_title: string | null;
  }>;
  offers: Array<{
    id: string;
    slug: string;
    title: string;
    summary: string | null;
    offer_type: string;
    offer_price_cents: number | null;
    currency: string;
    status: string;
    valid_until: string | null;
  }>;
};

export const businessPortalApi = {
  list: () => apiFetch<{ businesses: MyBusiness[] }>('/api/v1/my/businesses'),
  dashboard: (id: string, days = 30) => apiFetch<MyBusinessDashboard>(`/api/v1/my/businesses/${encodeURIComponent(id)}/dashboard?days=${days}`),
  claim: (slug: string, input: { claimantPhone?: string; relationship: string; message?: string }) =>
    apiFetch<{ claim: { id: string; status: string } }>(`/api/v1/my/businesses/${encodeURIComponent(slug)}/claims`, {
      method: 'POST', body: JSON.stringify(input),
    }),
  updateProfile: (id: string, input: Record<string, unknown>) =>
    apiFetch<{ ok: true }>(`/api/v1/my/businesses/${encodeURIComponent(id)}/profile`, {
      method: 'PATCH', body: JSON.stringify(input),
    }),
  createOffer: (id: string, input: Record<string, unknown>) =>
    apiFetch<{ offer: { id: string } }>(`/api/v1/my/businesses/${encodeURIComponent(id)}/offers`, {
      method: 'POST', body: JSON.stringify(input),
    }),
  updateLead: (businessId: string, leadId: string, input: Record<string, unknown>) =>
    apiFetch<{ ok: true; leadId: string; status: string }>(`/api/v1/my/businesses/${encodeURIComponent(businessId)}/leads/${encodeURIComponent(leadId)}`, {
      method: 'PATCH', body: JSON.stringify(input),
    }),
};
