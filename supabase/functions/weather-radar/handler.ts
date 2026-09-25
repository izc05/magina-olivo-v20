// `weather-radar` (Phase 20B, CR-006): RainViewer radar frames for the app's map.
// Request: { "operation": "frames" }. Response: provider, attribution, updatedAt and the
// past radar frames as tile URL templates. No key. The app shows the frame time; offline
// the radar says it needs a connection (the app never caches radar as "now").

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

export const RAINVIEWER_MAPS = "https://api.rainviewer.com/public/weather-maps.json";

export interface RadarFrame {
  time: string;
  tileUrlTemplate: string;
}

export interface RadarResponse {
  provider: "RAINVIEWER";
  providerName: string;
  attribution: string;
  updatedAt: string;
  fetchedAt: string;
  frames: RadarFrame[];
}

export interface Deps {
  fetch: Fetch;
  now: () => Date;
  timeoutMs?: number;
}

export interface Result {
  status: number;
  body: RadarResponse | { error: string };
}

interface Maps {
  generated?: number;
  host?: string;
  radar?: { past?: { time?: number; path?: string }[] };
}

export function parseFrames(doc: unknown, now: Date): RadarResponse {
  const maps = doc as Maps;
  const host = maps?.host;
  const past = maps?.radar?.past;
  if (typeof host !== "string" || !host.startsWith("https://") || !Array.isArray(past)) {
    throw new Error("rainviewer_invalid");
  }
  const frames = past
    .filter((f) => typeof f.time === "number" && typeof f.path === "string" && f.path.startsWith("/"))
    .map((f) => ({
      time: new Date(f.time! * 1000).toISOString(),
      // 256 px tiles, colour scheme 2 (universal blue), smoothed, snow shown.
      tileUrlTemplate: `${host}${f.path}/256/{z}/{x}/{y}/2/1_1.png`,
    }));
  if (frames.length === 0) throw new Error("rainviewer_empty");
  return {
    provider: "RAINVIEWER",
    providerName: "RainViewer",
    attribution: "Radar: RainViewer (rainviewer.com)",
    updatedAt: typeof maps.generated === "number" ? new Date(maps.generated * 1000).toISOString() : frames[frames.length - 1].time,
    fetchedAt: now.toISOString(),
    frames,
  };
}

export async function handleRadar(rawBody: unknown, deps: Deps): Promise<Result> {
  const operation = (rawBody as { operation?: unknown } | null)?.operation;
  if (operation !== "frames") return { status: 400, body: { error: "invalid_request" } };
  try {
    const response = await deps.fetch(RAINVIEWER_MAPS, {
      headers: { accept: "application/json" },
      signal: AbortSignal.timeout(deps.timeoutMs ?? 8000),
    });
    if (!response.ok) return { status: 502, body: { error: "radar_unavailable" } };
    return { status: 200, body: parseFrames(await response.json(), deps.now()) };
  } catch {
    return { status: 502, body: { error: "radar_unavailable" } };
  }
}
