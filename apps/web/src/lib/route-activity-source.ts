import { apiFetch } from './api-client';

export type RouteActivity = {
  id: string;
  route_id: string | null;
  route_slug: string | null;
  route_name: string | null;
  status: 'active' | 'paused' | 'completed';
  distance_m: number;
  active_seconds: number;
  active_started_at: string | null;
  point_count: number;
  started_at: string;
  ended_at: string | null;
  updated_at: string;
};

export type RouteActivitySummary = {
  summary: {
    completed_activities: number;
    recorded_distance_m: number;
    recorded_active_seconds: number;
    longest_activity_m: number;
  };
  recent: RouteActivity[];
  privacy: string;
};

export type RouteActivityTrack = {
  activity: RouteActivity;
  points: Array<{
    recorded_at: string;
    latitude: number;
    longitude: number;
    accuracy_m: number;
    altitude_m: number | null;
    segment_distance_m: number;
  }>;
};

export type RouteActivityInsights = {
  activity_id: string;
  recorded: {
    distance_m: number;
    active_seconds: number;
    average_speed_kmh: number;
  };
  quality: {
    average_accuracy_m: number | null;
    altitude_samples: number;
    min_altitude_m: number | null;
    max_altitude_m: number | null;
    elevation_gain_m: number;
    elevation_loss_m: number;
    elevation_method: 'gps_filtered';
  };
  official_route: null | {
    id: string;
    slug: string | null;
    name: string | null;
    distance_m: number | null;
    elevation_gain_m: number | null;
    duration_minutes: number | null;
    distance_delta_m: number | null;
    distance_delta_percent: number | null;
    elevation_delta_m: number | null;
    active_time_delta_minutes: number | null;
  };
  notice: string;
};

export function loadCurrentRouteActivity() {
  return apiFetch<{ activity: RouteActivity | null }>('/api/v1/activities/current');
}

export function loadRouteActivitySummary() {
  return apiFetch<RouteActivitySummary>('/api/v1/activities/me');
}

export function loadRouteActivityTrack(id: string) {
  return apiFetch<RouteActivityTrack>(`/api/v1/activities/${encodeURIComponent(id)}/track`);
}

export function loadRouteActivityInsights(id: string) {
  return apiFetch<RouteActivityInsights>(`/api/v1/activities/${encodeURIComponent(id)}/insights`);
}

export function startRouteActivity(routeId: string | null) {
  return apiFetch<{ activity: RouteActivity }>('/api/v1/activities/start', {
    method: 'POST',
    body: JSON.stringify({ route_id: routeId }),
  });
}

export function appendRouteActivityPoint(id: string, point: {
  latitude: number;
  longitude: number;
  accuracy_m: number;
  altitude_m?: number | null;
  recorded_at: string;
}) {
  return apiFetch<{
    accepted: boolean;
    reason?: 'low_accuracy' | 'sample_too_soon' | 'stationary' | 'implausible_jump';
    segment_distance_m?: number;
    activity: RouteActivity;
  }>(`/api/v1/activities/${encodeURIComponent(id)}/points`, {
    method: 'POST',
    body: JSON.stringify(point),
  });
}

export function pauseRouteActivity(id: string, keepalive = false) {
  return apiFetch<{ activity: RouteActivity }>(`/api/v1/activities/${encodeURIComponent(id)}/pause`, {
    method: 'POST',
    keepalive,
  });
}

export function resumeRouteActivity(id: string) {
  return apiFetch<{ activity: RouteActivity }>(`/api/v1/activities/${encodeURIComponent(id)}/resume`, { method: 'POST' });
}

export function finishRouteActivity(id: string) {
  return apiFetch<{ activity: RouteActivity }>(`/api/v1/activities/${encodeURIComponent(id)}/finish`, { method: 'POST' });
}

export function deleteRouteActivity(id: string) {
  return apiFetch<void>(`/api/v1/activities/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
