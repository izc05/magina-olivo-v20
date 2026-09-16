export type RouteGpsState = 'idle' | 'searching' | 'tracking' | 'error';

export type RouteLiveTelemetry = {
  activityId: string | null;
  activityStatus: 'recording' | 'paused' | 'completed' | 'idle';
  gpsState: RouteGpsState;
  latitude: number | null;
  longitude: number | null;
  accuracyM: number | null;
  liveDistanceM: number;
  liveElevationM: number;
  timestamp: number | null;
};

export const ROUTE_LIVE_TELEMETRY_EVENT = 'magina:route-live-telemetry';

const INITIAL_FIX_REPLAY_DELAYS_MS = [100, 500, 1500, 4000] as const;

let latestRouteLiveTelemetry: RouteLiveTelemetry | null = null;

export function getLatestRouteLiveTelemetry() {
  return latestRouteLiveTelemetry;
}

function dispatchRouteLiveTelemetry(detail: RouteLiveTelemetry) {
  window.dispatchEvent(new CustomEvent<RouteLiveTelemetry>(ROUTE_LIVE_TELEMETRY_EVENT, { detail }));
}

export function emitRouteLiveTelemetry(detail: RouteLiveTelemetry) {
  const previous = latestRouteLiveTelemetry;
  latestRouteLiveTelemetry = detail;
  if (typeof window === 'undefined') return;

  dispatchRouteLiveTelemetry(detail);

  const enteredTracking = detail.gpsState === 'tracking'
    && detail.latitude != null
    && detail.longitude != null
    && (previous?.gpsState !== 'tracking' || previous.activityId !== detail.activityId);
  if (!enteredTracking) return;

  const activityId = detail.activityId;
  for (const delay of INITIAL_FIX_REPLAY_DELAYS_MS) {
    window.setTimeout(() => {
      const latest = latestRouteLiveTelemetry;
      if (!latest || latest.gpsState !== 'tracking' || latest.activityId !== activityId) return;
      if (latest.latitude == null || latest.longitude == null) return;
      dispatchRouteLiveTelemetry(latest);
    }, delay);
  }
}

export function coordinateDistanceM(
  a: { latitude: number; longitude: number },
  b: { latitude: number; longitude: number },
) {
  const rad = Math.PI / 180;
  const lat1 = a.latitude * rad;
  const lat2 = b.latitude * rad;
  const dLat = (b.latitude - a.latitude) * rad;
  const dLon = (b.longitude - a.longitude) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}
