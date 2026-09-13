import { apiBaseUrl, apiFetch, apiFetchBlob } from './api-client';

export type RouteActivity = {
  id: string;
  user_id: string;
  route_id: string | null;
  route_slug: string | null;
  route_name: string | null;
  status: 'recording' | 'paused' | 'completed';
  visibility: 'private';
  current_segment: number;
  started_at: string;
  completed_at: string | null;
  distance_m: number;
  elevation_gain_m: number | null;
  duration_seconds: number;
  points_count: number;
  updated_at: string;
};

export type RouteActivityPointInput = {
  sequence: number;
  recorded_at: string;
  latitude: number;
  longitude: number;
  altitude_m?: number | null;
  horizontal_accuracy_m?: number | null;
  vertical_accuracy_m?: number | null;
};

export type RecordedJourneySummary = {
  activity_count: number;
  recorded_distance_m: number;
  recorded_duration_seconds: number;
  recorded_elevation_gain_m: number;
  longest_activity_m: number;
};

export async function loadActiveRouteActivity() {
  return apiFetch<{ activity: RouteActivity | null }>('/api/v1/activities/active');
}

export async function startRouteActivity(routeId: string) {
  return apiFetch<{ activity: RouteActivity; resumed_existing?: boolean }>(`/api/v1/routes/${encodeURIComponent(routeId)}/activities/start`, { method: 'POST' });
}

export async function appendRouteActivityPoints(activityId: string, points: RouteActivityPointInput[]) {
  return apiFetch<{ accepted: number; ignored_duplicates: number }>(`/api/v1/activities/${encodeURIComponent(activityId)}/points`, {
    method: 'POST',
    body: JSON.stringify({ points }),
  });
}

export async function pauseRouteActivity(activityId: string) {
  return apiFetch<{ activity: RouteActivity }>(`/api/v1/activities/${encodeURIComponent(activityId)}/pause`, { method: 'POST' });
}

export async function resumeRouteActivity(activityId: string) {
  return apiFetch<{ activity: RouteActivity }>(`/api/v1/activities/${encodeURIComponent(activityId)}/resume`, { method: 'POST' });
}

export async function finishRouteActivity(activityId: string) {
  return apiFetch<{ activity: RouteActivity; already_completed?: boolean }>(`/api/v1/activities/${encodeURIComponent(activityId)}/finish`, { method: 'POST' });
}

export async function loadRouteActivities(limit = 20) {
  return apiFetch<{ activities: RouteActivity[] }>(`/api/v1/activities/me?limit=${Math.max(1, Math.min(100, Math.round(limit)))}`);
}

export async function deleteRouteActivity(activityId: string) {
  return apiFetch<void>(`/api/v1/activities/${encodeURIComponent(activityId)}`, { method: 'DELETE' });
}

export async function loadRecordedJourneySummary() {
  const profile = await apiFetch<{ recorded: RecordedJourneySummary }>('/api/v1/adventures/me');
  return profile.recorded;
}

export function activityGpxUrl(activityId: string) {
  return `${apiBaseUrl}/api/v1/activities/${encodeURIComponent(activityId)}/gpx`;
}

export async function downloadRouteActivityGpx(activityId: string) {
  return apiFetchBlob(`/api/v1/activities/${encodeURIComponent(activityId)}/gpx`);
}
