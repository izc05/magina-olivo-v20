// Orchestration, free of Deno APIs so it runs under the fixture tests.
// Chain: AEMET -> MET Norway. A second provider is asked only when the first fails
// (error, timeout, rate limit, invalid document). The app holds the last valid value.

import {
  type Current,
  type DailyForecast,
  type ForecastRequest,
  type Place,
  type ProviderId,
  PROVIDERS,
  ProviderError,
  type WeatherResponse,
} from "./contract.ts";
import { aemetDocument, parseAemetDaily, parseAemetHourly } from "./aemet.ts";
import { metnoDocument, parseMetNo } from "./metno.ts";
import { type MasterEntry, resolveMunicipality } from "./municipalities.ts";
import { solarTimesFor } from "./solar.ts";

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

export interface Deps {
  fetch: Fetch;
  /** AEMET key from the function secrets; absent -> AEMET is skipped, MET Norway still answers. */
  aemetApiKey: string | undefined;
  now: () => Date;
  timeoutMs?: number;
  log?: (message: string) => void;
}

export interface Result {
  status: number;
  body: WeatherResponse | { error: string; detail?: string; candidates?: string[] };
}

// The municipality list changes a few times a year; keep it for the life of the instance.
let masterCache: { list: MasterEntry[]; at: number } | null = null;
const MASTER_TTL_MS = 24 * 60 * 60 * 1000;

export function resetMasterCache(): void {
  masterCache = null;
}

async function master(deps: Deps, timeoutMs: number): Promise<MasterEntry[] | null> {
  if (masterCache && deps.now().getTime() - masterCache.at < MASTER_TTL_MS) return masterCache.list;
  if (!deps.aemetApiKey) return null;
  try {
    const list = await aemetDocument("/maestro/municipios", deps.aemetApiKey, deps.fetch, timeoutMs);
    if (!Array.isArray(list) || list.length === 0) return null;
    masterCache = { list: list as MasterEntry[], at: deps.now().getTime() };
    return masterCache.list;
  } catch (error) {
    deps.log?.(`municipalities unavailable: ${(error as Error).message}`);
    return null;
  }
}

function validRequest(body: unknown): ForecastRequest | null {
  if (!body || typeof body !== "object") return null;
  const b = body as Record<string, unknown>;
  const str = (v: unknown) => (typeof v === "string" && v.trim() ? v.trim().slice(0, 120) : undefined);
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : undefined);
  const request: ForecastRequest = {
    municipalityCode: str(b.municipalityCode),
    municipality: str(b.municipality),
    province: str(b.province),
    latitude: num(b.latitude),
    longitude: num(b.longitude),
  };
  if (request.municipalityCode && !/^\d{5}$/.test(request.municipalityCode)) return null;
  if (request.latitude !== undefined && Math.abs(request.latitude) > 90) return null;
  if (request.longitude !== undefined && Math.abs(request.longitude) > 180) return null;
  if (!request.municipalityCode && !request.municipality) return null;
  return request;
}

function solarTimeZone(place: Place): string {
  const provinceCode = place.code.slice(0, 2);
  return provinceCode === "35" || provinceCode === "38" ? "Atlantic/Canary" : "Europe/Madrid";
}

function respond(provider: ProviderId, place: Place, reading: { current: Current; daily: DailyForecast[]; updatedAt: string }, now: Date): Result {
  return {
    status: 200,
    body: {
      provider,
      providerName: PROVIDERS[provider].name,
      attribution: PROVIDERS[provider].attribution,
      updatedAt: reading.updatedAt,
      fetchedAt: now.toISOString(),
      location: { code: place.code, name: place.name, province: place.province },
      current: reading.current,
      daily: reading.daily,
      solar: solarTimesFor(now, place.latitude, place.longitude, solarTimeZone(place)),
    },
  };
}

export async function handleForecast(rawBody: unknown, deps: Deps): Promise<Result> {
  const request = validRequest(rawBody);
  if (!request) return { status: 400, body: { error: "invalid_request" } };
  const timeoutMs = deps.timeoutMs ?? 8000;

  const list = await master(deps, timeoutMs);
  let place: Place | null = null;
  if (list) {
    const resolution = resolveMunicipality(list, request);
    if (resolution.kind === "not_found") return { status: 404, body: { error: "municipality_not_found" } };
    if (resolution.kind === "ambiguous") {
      return { status: 409, body: { error: "municipality_ambiguous", candidates: resolution.candidates.slice(0, 10) } };
    }
    place = resolution.place;
  }

  // A provider's current reading without its week: answered only when no provider has a week, so
  // the week never goes missing silently and a missing week never costs the current weather.
  let currentOnly: { provider: ProviderId; place: Place; current: Current; updatedAt: string } | null = null;

  // 1. AEMET (needs the INE code, hence the resolved place). Hourly and daily are asked in parallel
  //    so the chain stays inside the app's 10 s budget (as before the week existed).
  if (place && deps.aemetApiKey) {
    const key = deps.aemetApiKey;
    const [hourlyResult, dailyResult] = await Promise.allSettled([
      aemetDocument(`/prediccion/especifica/municipio/horaria/${place.code}`, key, deps.fetch, timeoutMs)
        .then((doc) => parseAemetHourly(doc, deps.now())),
      aemetDocument(`/prediccion/especifica/municipio/diaria/${place.code}`, key, deps.fetch, timeoutMs)
        .then((doc) => parseAemetDaily(doc, deps.now())),
    ]);
    if (hourlyResult.status === "fulfilled" && dailyResult.status === "fulfilled") {
      const hourly = hourlyResult.value;
      const daily = dailyResult.value;
      // The combined response is no newer than either provider product.
      const updatedAt = new Date(Math.min(Date.parse(hourly.updatedAt), Date.parse(daily.updatedAt))).toISOString();
      return respond("AEMET", place, { current: hourly.current, daily: daily.daily, updatedAt }, deps.now());
    }
    if (hourlyResult.status === "fulfilled") {
      currentOnly = { provider: "AEMET", place, current: hourlyResult.value.current, updatedAt: hourlyResult.value.updatedAt };
    }
    const reasons = [hourlyResult, dailyResult]
      .filter((result): result is PromiseRejectedResult => result.status === "rejected")
      .map((result) => (result.reason as Error).message);
    deps.log?.(`AEMET incomplete, falling back: ${reasons.join(", ")}`);
  }

  // 2. MET Norway (needs coordinates: from the resolved place, else from the request).
  const latitude = place?.latitude ?? request.latitude;
  const longitude = place?.longitude ?? request.longitude;
  if (latitude !== undefined && latitude !== null && longitude !== undefined && longitude !== null) {
    try {
      const doc = await metnoDocument(latitude, longitude, deps.fetch, timeoutMs);
      const fallbackPlace: Place = place ?? {
        code: request.municipalityCode ?? "",
        name: request.municipality ?? "",
        province: request.province ?? null,
        latitude,
        longitude,
      };
      const reading = parseMetNo(doc, deps.now());
      if (reading.daily.length > 0) return respond("MET_NORWAY", fallbackPlace, reading, deps.now());
      currentOnly ??= { provider: "MET_NORWAY", place: fallbackPlace, current: reading.current, updatedAt: reading.updatedAt };
      deps.log?.("MET Norway gave no usable week");
    } catch (error) {
      deps.log?.(`MET Norway failed: ${(error as Error).message}`);
    }
  }

  // 3. No provider has a week: the current weather of one provider, with an empty week.
  if (currentOnly) {
    return respond(currentOnly.provider, currentOnly.place, { current: currentOnly.current, daily: [], updatedAt: currentOnly.updatedAt }, deps.now());
  }
  return { status: 502, body: { error: "providers_unavailable" } };
}

export { ProviderError };
