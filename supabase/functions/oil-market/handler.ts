// `oil-market` (Phase 20D): official olive-oil prices as normalized JSON for the app.
// Request: { "operation": "series", "geography": "andalucia", "weeks": 12 } or
//          { "operation": "latest", "geography": "andalucia" }.
// Response: docs/07-plans/PHASE20D-OIL-MARKET-CONTRACT.md — one series per category, each
// observation with its week, the value as published (€/kg) and the source. Nothing is averaged,
// interpolated or filled in; if the page cannot be read the answer is 502 and the app keeps what
// it has. No key.

import { type Category, JUNTA_SOURCE_ID, JUNTA_SOURCE_NAME, JUNTA_URL, parseJuntaPage } from "./junta.ts";

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

export interface Observation {
  periodStart: string;
  periodEnd: string;
  originalValue: number;
  originalUnit: "EUR_PER_KG";
  valueEurPerKg: number;
}

export interface Series {
  sourceId: string;
  sourceName: string;
  sourceUrl: string;
  geography: { level: "AUTONOMOUS_COMMUNITY"; code: "ES-AN"; name: "Andalucía" };
  category: Category;
  marketStage: "ALMAZARA_OR_BODEGA";
  observations: Observation[];
}

export interface OilMarketResponse {
  provider: "oil-market";
  fetchedAt: string;
  attribution: string;
  series: Series[];
}

export interface Deps {
  fetch: Fetch;
  now: () => Date;
  timeoutMs?: number;
}

export interface Result {
  status: number;
  body: OilMarketResponse | { error: string };
}

const CATEGORIES: Category[] = ["AOVE", "AOV", "AOL"];
const MAX_WEEKS = 52;

export async function handleOilMarket(rawBody: unknown, deps: Deps): Promise<Result> {
  const request = rawBody as { operation?: unknown; geography?: unknown; weeks?: unknown } | null;
  const operation = request?.operation;
  if (operation !== "series" && operation !== "latest") return { status: 400, body: { error: "invalid_request" } };
  if (request?.geography !== "andalucia") return { status: 400, body: { error: "unsupported_geography" } };
  let weeks = 1;
  if (operation === "series") {
    weeks = request?.weeks === undefined ? 12 : Number(request.weeks);
    if (!Number.isInteger(weeks) || weeks < 1 || weeks > MAX_WEEKS) return { status: 400, body: { error: "invalid_request" } };
  }
  const now = deps.now();
  let html: string;
  try {
    const response = await deps.fetch(JUNTA_URL, {
      headers: { accept: "text/html" },
      signal: AbortSignal.timeout(deps.timeoutMs ?? 10_000),
    });
    if (!response.ok) return { status: 502, body: { error: "source_unavailable" } };
    html = await response.text();
  } catch {
    return { status: 502, body: { error: "source_unavailable" } };
  }
  let values;
  try {
    values = parseJuntaPage(html, now);
  } catch (failure) {
    // The page changed or says something we do not understand: refuse rather than guess.
    console.error("oil-market: junta page not understood:", (failure as Error).message);
    return { status: 502, body: { error: "source_unreadable" } };
  }
  // The window is the latest `weeks` published weeks for all categories together, so a category
  // missing a week shows the gap instead of reaching back for an older week.
  const periods = new Set([...new Set(values.map((value) => value.periodStart))].sort().slice(-weeks));
  const series = CATEGORIES.map((category): Series => {
    const own = values
      .filter((value) => value.category === category && periods.has(value.periodStart))
      .sort((a, b) => a.periodStart.localeCompare(b.periodStart));
    return {
      sourceId: JUNTA_SOURCE_ID,
      sourceName: JUNTA_SOURCE_NAME,
      sourceUrl: JUNTA_URL,
      geography: { level: "AUTONOMOUS_COMMUNITY", code: "ES-AN", name: "Andalucía" },
      category,
      marketStage: "ALMAZARA_OR_BODEGA",
      observations: own.map((value) => ({
        periodStart: value.periodStart,
        periodEnd: value.periodEnd,
        originalValue: Number(value.value),
        originalUnit: "EUR_PER_KG",
        valueEurPerKg: Number(value.value),
      })),
    };
  }).filter((entry) => entry.observations.length > 0);
  return {
    status: 200,
    body: {
      provider: "oil-market",
      fetchedAt: now.toISOString(),
      attribution: `Fuente: ${JUNTA_SOURCE_NAME}`,
      series,
    },
  };
}
