// AEMET OpenData: hourly municipal forecast, two-step (metadata -> `datos` URL).
// The API key is read by index.ts from the function's secrets; it never reaches the app.

import { type Condition, type Current, ProviderError } from "./contract.ts";
import { madridDateHour, madridLocalToIso } from "./time.ts";

export const AEMET_BASE = "https://opendata.aemet.es/opendata/api";

/** AEMET sky state ("12", "12n") -> contract condition; null when the code is unknown. */
export function aemetCondition(code: string): Condition | null {
  const n = Number(code.replace(/n$/, ""));
  if (n === 11) return "CLEAR";
  if (n === 12 || n === 13 || n === 17) return "PARTLY_CLOUDY";
  if (n >= 14 && n <= 16) return "CLOUDY";
  if ((n >= 23 && n <= 26) || (n >= 43 && n <= 46)) return "RAIN";
  if ((n >= 33 && n <= 36) || (n >= 71 && n <= 74)) return "SNOW";
  if ((n >= 51 && n <= 54) || (n >= 61 && n <= 64)) return "STORM";
  if (n === 81 || n === 82) return "FOG";
  if (n === 83) return "HAZE";
  return null;
}

interface Timed { value?: string; periodo?: string }
interface Wind { direccion?: string[]; velocidad?: string[]; value?: string; periodo?: string }
interface Day {
  fecha: string;
  estadoCielo?: Timed[];
  temperatura?: Timed[];
  probPrecipitacion?: Timed[];
  vientoAndRachaMax?: Wind[];
}
interface Forecast { elaborado?: string; prediccion?: { dia?: Day[] } }

function hourOf(periodo: string | undefined): number | null {
  if (!periodo || periodo.length !== 2) return null;
  const h = Number(periodo);
  return Number.isInteger(h) ? h : null;
}

/** "0814" covers hours 08..13; "2002" wraps past midnight. */
function inRange(periodo: string | undefined, hour: number): boolean {
  if (!periodo || periodo.length !== 4) return false;
  const from = Number(periodo.slice(0, 2));
  const to = Number(periodo.slice(2));
  return from < to ? hour >= from && hour < to : hour >= from || hour < to;
}

/**
 * The first forecast hour at or after `now` (Madrid time). Throws when the document has no
 * usable hour: an invalid response is a provider failure, never a guessed value.
 */
export function parseAemetHourly(doc: unknown, now: Date): { current: Current; updatedAt: string } {
  const forecast = (Array.isArray(doc) ? doc[0] : doc) as Forecast | undefined;
  const days = forecast?.prediccion?.dia;
  if (!Array.isArray(days) || days.length === 0) throw new ProviderError("aemet_invalid");
  const { date: today, hour: nowHour } = madridDateHour(now);

  for (const day of days) {
    const date = String(day.fecha ?? "").slice(0, 10);
    if (date < today) continue;
    const temps = (day.temperatura ?? [])
      .map((t) => ({ hour: hourOf(t.periodo), value: Number(t.value) }))
      .filter((t) => t.hour !== null && Number.isFinite(t.value))
      .sort((a, b) => a.hour! - b.hour!);
    const slot = temps.find((t) => date > today || t.hour! >= nowHour);
    if (!slot) continue;
    const hour = slot.hour!;
    const sky = (day.estadoCielo ?? []).find((s) => hourOf(s.periodo) === hour);
    const condition = sky?.value ? aemetCondition(sky.value) : null;
    if (!condition) throw new ProviderError("aemet_invalid");
    const rain = (day.probPrecipitacion ?? []).find((p) => inRange(p.periodo, hour));
    const wind = (day.vientoAndRachaMax ?? []).find((w) => hourOf(w.periodo) === hour && w.velocidad);
    const rainValue = rain?.value !== undefined && rain.value !== "" ? Number(rain.value) : NaN;
    const windValue = wind?.velocidad?.[0] !== undefined ? Number(wind.velocidad[0]) : NaN;
    const hh = String(hour).padStart(2, "0");
    return {
      current: {
        validAt: madridLocalToIso(`${date}T${hh}:00:00`),
        temperatureC: Math.round(slot.value),
        condition,
        rainProbabilityPercent: Number.isFinite(rainValue) ? Math.round(rainValue) : null,
        windKmh: Number.isFinite(windValue) ? Math.round(windValue) : null,
      },
      updatedAt: forecast?.elaborado ? madridLocalToIso(forecast.elaborado) : now.toISOString(),
    };
  }
  throw new ProviderError("aemet_no_current_hour");
}

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

/** AEMET's documents come as ISO-8859-15 unless the header says otherwise. */
async function decode(response: Response): Promise<unknown> {
  const type = response.headers.get("content-type") ?? "";
  const charset = /charset=([^;]+)/i.exec(type)?.[1]?.trim().toLowerCase() ?? "iso-8859-15";
  const text = new TextDecoder(charset).decode(await response.arrayBuffer());
  return JSON.parse(text);
}

/** Metadata call, then the document it points to. Any non-success is a ProviderError. */
export async function aemetDocument(path: string, apiKey: string, fetchFn: Fetch, timeoutMs: number): Promise<unknown> {
  const meta = await fetchFn(`${AEMET_BASE}${path}`, {
    headers: { api_key: apiKey, accept: "application/json" },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (meta.status === 429) throw new ProviderError("aemet_rate_limited");
  if (!meta.ok) throw new ProviderError(`aemet_http_${meta.status}`);
  const body = (await decode(meta)) as { estado?: number; datos?: string } | unknown[];
  if (Array.isArray(body)) return body; // some master endpoints answer directly
  if (body.estado !== 200 || !body.datos) throw new ProviderError(`aemet_estado_${body.estado ?? "none"}`);
  const data = await fetchFn(body.datos, { signal: AbortSignal.timeout(timeoutMs) });
  if (data.status === 429) throw new ProviderError("aemet_rate_limited");
  if (!data.ok) throw new ProviderError(`aemet_http_${data.status}`);
  return decode(data);
}
