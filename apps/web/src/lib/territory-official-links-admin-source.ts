import { apiFetch } from './api-client';

export type AdminMunicipalitySummary = {
  id: string;
  ine_code: string;
  name: string;
  slug: string;
  province_name: string;
  active: boolean;
};

export type AdminMunicipalityOfficialLink = {
  id: string;
  municipality_id: string;
  municipality_name: string;
  municipality_slug: string;
  kind: 'town_hall' | 'electronic_office' | 'transparency' | 'tourism' | 'other_official';
  label: string;
  url: string;
  source_url: string | null;
  verified_at: string | null;
  active: boolean;
  sort_order: number;
  created_at: string;
  updated_at: string;
};

export type OfficialLinkInput = {
  municipality_id: string;
  kind: AdminMunicipalityOfficialLink['kind'];
  label: string;
  url: string;
  source_url: string | null;
  verified: boolean;
  active: boolean;
  sort_order: number;
};

export async function loadAdminMunicipalityOfficialLinks() {
  return apiFetch<{ municipalities: AdminMunicipalitySummary[]; links: AdminMunicipalityOfficialLink[] }>('/api/v1/admin/territory/official-links');
}

export async function createAdminMunicipalityOfficialLink(input: OfficialLinkInput) {
  return apiFetch<{ link: AdminMunicipalityOfficialLink }>('/api/v1/admin/territory/official-links', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export async function updateAdminMunicipalityOfficialLink(id: string, input: Partial<Omit<OfficialLinkInput, 'municipality_id'>>) {
  return apiFetch<{ link: AdminMunicipalityOfficialLink }>(`/api/v1/admin/territory/official-links/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export async function deleteAdminMunicipalityOfficialLink(id: string) {
  return apiFetch<{ deleted: true }>(`/api/v1/admin/territory/official-links/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
