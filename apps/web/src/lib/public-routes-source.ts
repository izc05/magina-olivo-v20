import { apiFetch } from './api-client';

export type PublicRouteSummary = {
  id: string;
  slug: string;
  name: string;
  route_type: 'hiking' | 'mtb' | 'cycling' | 'trail' | 'family' | 'mixed';
  difficulty: 'easy' | 'moderate' | 'hard' | 'very_hard' | null;
  distance_m: number | null;
  duration_minutes: number | null;
  elevation_gain_m: number | null;
  elevation_loss_m: number | null;
  circular: boolean;
  family_friendly: boolean;
  short_description: string | null;
  municipality_name: string | null;
  place_name: string | null;
  hero_url: string | null;
};

export type PublicRouteDetail = {
  route: Record<string, unknown> & {
    id: string;
    slug: string;
    name: string;
    route_type: PublicRouteSummary['route_type'];
    difficulty: PublicRouteSummary['difficulty'];
    distance_m: number | null;
    duration_minutes: number | null;
    elevation_gain_m: number | null;
    elevation_loss_m: number | null;
    min_altitude_m: number | null;
    max_altitude_m: number | null;
    circular: boolean;
    family_friendly: boolean;
    short_description: string | null;
    description: string | null;
    safety_notes: string | null;
    access_notes: string | null;
    water_notes: string | null;
    municipality_name: string | null;
    place_name: string | null;
  };
  track: null | {
    id: string;
    version: number;
    geometry: { type: 'LineString' | 'MultiLineString'; coordinates: unknown };
    bbox: [number, number, number, number] | null;
    source_name: string | null;
    source_url: string | null;
    validated_at: string | null;
  };
  elevation: Array<{
    sample_order: number;
    distance_m: number;
    elevation_m: number;
    grade_percent: number | null;
    latitude: number | null;
    longitude: number | null;
  }>;
  points: Array<Record<string, unknown>>;
  media: Array<Record<string, unknown>>;
  sources: Array<Record<string, unknown>>;
  segments: Array<Record<string, unknown>>;
};

export async function loadPublicRoutes(query = '') {
  const suffix = query.trim() ? `?q=${encodeURIComponent(query.trim())}` : '';
  const response = await apiFetch<{ routes: PublicRouteSummary[] }>(`/api/v1/public/routes${suffix}`);
  return response.routes;
}

export async function loadPublicRoute(slug: string) {
  return apiFetch<PublicRouteDetail>(`/api/v1/public/routes/${encodeURIComponent(slug)}`);
}
