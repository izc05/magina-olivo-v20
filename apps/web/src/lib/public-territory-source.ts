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
