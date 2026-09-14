import { apiBaseUrl } from '@/lib/api-client';

export type PublicMunicipalityOfficialLink = {
  id: string;
  kind: 'town_hall' | 'electronic_office' | 'transparency' | 'tourism' | 'other_official' | string;
  label: string;
  url: string;
  source_url: string | null;
  verified_at: string;
};

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
  official_links?: PublicMunicipalityOfficialLink[];
};

export type PublicMunicipalityContentType = 'place' | 'mill' | 'directory' | 'news' | 'event';

export type PublicMunicipalityContent = {
  id: string;
  type: PublicMunicipalityContentType;
  slug: string;
  title: string;
  summary: string | null;
  content_json: Record<string, unknown> | null;
  featured: boolean;
  starts_at: string | null;
  ends_at: string | null;
  media_url: string | null;
  external_url: string | null;
  sort_order: number;
  published_at: string | null;
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
  content_counts: Record<PublicMunicipalityContentType, number>;
  related_content?: PublicMunicipalityContent[];
};

export class PublicTerritoryUnavailableError extends Error {
  constructor() {
    super('public_territory_unavailable');
    this.name = 'PublicTerritoryUnavailableError';
  }
}

async function publicTerritoryFetch<T>(path: string): Promise<T> {
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
  const payload = await publicTerritoryFetch<{ places?: PublicTerritoryPlace[] }>('/api/v1/public/territory/places');
  return Array.isArray(payload.places) ? payload.places : [];
}

export async function loadPublicTerritoryPlace(slug: string) {
  const normalized = slug.trim().toLocaleLowerCase('es-ES');
  if (!normalized) throw new PublicTerritoryUnavailableError();
  const payload = await publicTerritoryFetch<{ place?: PublicTerritoryPlace }>(`/api/v1/public/territory/places/${encodeURIComponent(normalized)}`);
  if (!payload.place) throw new PublicTerritoryUnavailableError();
  return payload.place;
}

export async function loadPublicMunicipalities() {
  const payload = await publicTerritoryFetch<{ municipalities?: PublicMunicipalityDirectory[] }>('/api/v1/public/territory/municipalities');
  return Array.isArray(payload.municipalities) ? payload.municipalities : [];
}

export async function loadPublicMunicipality(slug: string) {
  const normalized = slug.trim().toLocaleLowerCase('es-ES');
  if (!normalized) throw new PublicTerritoryUnavailableError();
  const payload = await publicTerritoryFetch<{ municipality?: PublicMunicipalityDirectory }>(`/api/v1/public/territory/municipalities/${encodeURIComponent(normalized)}`);
  if (!payload.municipality) throw new PublicTerritoryUnavailableError();
  return payload.municipality;
}
