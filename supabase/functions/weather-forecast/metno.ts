// MET Norway Locationforecast 2.0 (compact), keyless. Terms: identify the client in the
// User-Agent and credit the source (CC BY 4.0). Used only when AEMET fails.

import { type Condition, type Current, ProviderError } from "./contract.ts";

export const METNO_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact";
export const METNO_USER_AGENT = "MaginaOlivo/1.0 (+https://github.com/izc05/magina-olivo-v20)";

export function metnoCondition(symbol: string): Condition | null {
  const s = symbol.replace(/_(day|night|polartwilight)$/, "");
  if (s.includes("thunder")) return "STORM";
  if (s.includes("snow") || s.includes("sleet")) return "SNOW";
  if (s.includes("rain")) return "RAIN";
  if (s === "fog") return "FOG";
  if (s === "clearsky") return "CLEAR";
  if (s === "fair" || s === "partlycloudy") return "PARTLY_CLOUDY";
  if (s === "cloudy") return "CLOUDY";
  return null;
}

interface Step {
  time: string;
  data?: {
    instant?: { details?: { air_temperature?: number; wind_speed?: number } };
    next_1_hours?: { summary?: { symbol_code?: string } };
    next_6_hours?: { summary?: { symbol_code?: string } };
  };
}

/** The step covering `now` (the latest one not after it), else the first future one. */
export function parseMetNo(doc: unknown, now: Date): { current: Current; updatedAt: string } {
  const props = (doc as { properties?: { meta?: { updated_at?: string }; timeseries?: Step[] } })?.properties;
  const series = props?.timeseries;
  if (!Array.isArray(series) || series.length === 0) throw new ProviderError("metno_invalid");
  const past = series.filter((s) => Date.parse(s.time) <= now.getTime());
  const step = past.length > 0 && now.getTime() - Date.parse(past[past.length - 1].time) < 60 * 60 * 1000
    ? past[past.length - 1]
    : series.find((s) => Date.parse(s.time) > now.getTime());
  const details = step?.data?.instant?.details;
  const symbol = step?.data?.next_1_hours?.summary?.symbol_code ?? step?.data?.next_6_hours?.summary?.symbol_code;
  const condition = symbol ? metnoCondition(symbol) : null;
  if (!step || typeof details?.air_temperature !== "number" || !condition) throw new ProviderError("metno_invalid");
  return {
    current: {
      validAt: new Date(Date.parse(step.time)).toISOString(),
      temperatureC: Math.round(details.air_temperature),
      condition,
      // The compact product has no precipitation probability: unknown, never 0.
      rainProbabilityPercent: null,
      windKmh: typeof details.wind_speed === "number" ? Math.round(details.wind_speed * 3.6) : null,
    },
    updatedAt: props?.meta?.updated_at ? new Date(Date.parse(props.meta.updated_at)).toISOString() : now.toISOString(),
  };
}

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

export async function metnoDocument(latitude: number, longitude: number, fetchFn: Fetch, timeoutMs: number): Promise<unknown> {
  // MET asks for at most 4 decimals.
  const url = `${METNO_URL}?lat=${latitude.toFixed(4)}&lon=${longitude.toFixed(4)}`;
  const response = await fetchFn(url, {
    headers: { "user-agent": METNO_USER_AGENT, accept: "application/json" },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!response.ok) throw new ProviderError(`metno_http_${response.status}`);
  return response.json();
}
