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

export async function loadPublicTerritoryPlaces() {
  if (!apiBaseUrl) throw new PublicTerritoryUnavailableError();
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}/api/v1/public/territory/places`, {
      headers: { accept: 'application/json' },
    });
  } catch {
    throw new PublicTerritoryUnavailableError();
  }
  if (!response.ok) throw new PublicTerritoryUnavailableError();
  const payload = await response.json() as { places?: PublicTerritoryPlace[] };
  return Array.isArray(payload.places) ? payload.places : [];
}
