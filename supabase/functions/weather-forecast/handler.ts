// Orchestration, free of Deno APIs so it runs under the fixture tests.
// Chain: AEMET -> MET Norway. A second provider is asked only when the first fails
// (error, timeout, rate limit, invalid document). The app holds the last valid value.

import {
  type Current,
  type ForecastRequest,
  type Place,
  type ProviderId,
  PROVIDERS,
  ProviderError,
  type WeatherResponse,
} from "./contract.ts";
import { aemetDocument, parseAemetHourly } from "./aemet.ts";
import { metnoDocument, parseMetNo } from "./metno.ts";
import { type MasterEntry, resolveMunicipality } from "./municipalities.ts";

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

function respond(provider: ProviderId, place: Place, reading: { current: Current; updatedAt: string }, now: Date): Result {
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

  // 1. AEMET (needs the INE code, hence the resolved place).
  if (place && deps.aemetApiKey) {
    try {
      const doc = await aemetDocument(
        `/prediccion/especifica/municipio/horaria/${place.code}`,
        deps.aemetApiKey,
        deps.fetch,
        timeoutMs,
      );
      return respond("AEMET", place, parseAemetHourly(doc, deps.now()), deps.now());
    } catch (error) {
      deps.log?.(`AEMET failed, falling back: ${(error as Error).message}`);
    }
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
      return respond("MET_NORWAY", fallbackPlace, parseMetNo(doc, deps.now()), deps.now());
    } catch (error) {
      deps.log?.(`MET Norway failed: ${(error as Error).message}`);
    }
  }

  return { status: 502, body: { error: "providers_unavailable" } };
}

export { ProviderError };
