import { apiBaseUrl } from '@/lib/api-client';

export type PublicTerritoryPlace = {
  id: string;
  name: string;
  slug: string;
  kind: 'municipal_seat' | 'locality' | 'hamlet' | 'other' | string;
  hero_asset_key: string | null;
  center: { type?: string; coordinates?: number[] } | null;
  municipality_id: string;
  municipality_name: string;
  municipality_slug: string;
  ine_code: string;
  aemet_code: string | null;
  province_name: string;
};

export type PublicMunicipalityDirectory = {
  id: string;
  ine_code: string;
  aemet_code: string | null;
  name: string;
  slug: string;
  province_name: string;
  center: { type?: string; coordinates?: number[] } | null;
  official_website: string;
  electronic_office_url: string | null;
  transparency_url: string | null;
  tourism_url: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  postal_code: string | null;
  source_url: string;
  verified_at: string;
  places: Array<{ id: string; name: string; slug: string; kind: string }>;
};

export class PublicTerritoryUnavailableError extends Error {
  constructor() {
    super('public_territory_unavailable');
    this.name = 'PublicTerritoryUnavailableError';
  }
}

async function publicJson<T>(path: string): Promise<T> {
  if (!apiBaseUrl) throw new PublicTerritoryUnavailableError();
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, { headers: { accept: 'application/json' } });
  } catch {
    throw new PublicTerritoryUnavailableError();
  }
  if (!response.ok) throw new PublicTerritoryUnavailableError();
  return response.json() as Promise<T>;
}

export async function loadPublicTerritoryPlaces() {
  const payload = await publicJson<{ places?: PublicTerritoryPlace[] }>('/api/v1/public/territory/places');
  return Array.isArray(payload.places) ? payload.places : [];
}

export async function loadPublicMunicipalities() {
  const payload = await publicJson<{ municipalities?: PublicMunicipalityDirectory[] }>('/api/v1/public/territory/municipalities');
  return Array.isArray(payload.municipalities) ? payload.municipalities : [];
}

export async function loadPublicMunicipality(slug: string) {
  const payload = await publicJson<{ municipality?: PublicMunicipalityDirectory }>(`/api/v1/public/territory/municipalities/${encodeURIComponent(slug)}`);
  if (!payload.municipality) throw new PublicTerritoryUnavailableError();
  return payload.municipality;
}
