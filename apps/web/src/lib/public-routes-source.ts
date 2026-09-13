import { apiBaseUrl, apiFetch } from './api-client';

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

export type PublicRouteCommunity = {
  route: { id: string; name: string };
  summary: { review_count: number; rating_average: number | string; completed_reviews: number };
  reviews: Array<{
    id: string; rating: number; title: string | null; body: string | null; visited_on: string | null;
    completed: boolean; difficulty_vote: string | null; helpful_count: number; published_at: string | null;
    created_at: string; display_name: string | null;
  }>;
  conditions: Array<{
    id: string; condition_kind: string; severity: string; note: string | null; observed_at: string;
    expires_at: string | null; latitude: number | null; longitude: number | null;
  }>;
  photos: Array<{
    id: string; review_id: string; media_asset_id: string; caption: string | null; captured_at: string | null;
    latitude: number | null; longitude: number | null; mime_type: string; url: string;
  }>;
  sponsorships: Array<{
    id: string; sponsor_name: string; sponsor_logo_url: string | null; sponsor_url: string | null;
    headline: string | null; description: string | null; cta_label: string | null; cta_url: string | null;
    promo_code: string | null; placement: string; billing_model: string; disclosure: string;
  }>;
  notices: { community_conditions: string; sponsored_content: string };
};

export async function loadPublicRoutes(query = '') {
  const suffix = query.trim() ? `?q=${encodeURIComponent(query.trim())}` : '';
  const response = await apiFetch<{ routes: PublicRouteSummary[] }>(`/api/v1/public/routes${suffix}`);
  return response.routes;
}

export async function loadPublicRoute(slug: string) {
  return apiFetch<PublicRouteDetail>(`/api/v1/public/routes/${encodeURIComponent(slug)}`);
}

export async function loadPublicRouteCommunity(slug: string) {
  return apiFetch<PublicRouteCommunity>(`/api/v1/public/routes/${encodeURIComponent(slug)}/community`);
}

export function routeGpxUrl(slug: string) {
  return `${apiBaseUrl}/api/v1/public/routes/${encodeURIComponent(slug)}/gpx`;
}

export function publicRouteMediaUrl(path: string) {
  if (/^https?:\/\//i.test(path)) return path;
  return `${apiBaseUrl}${path}`;
}
