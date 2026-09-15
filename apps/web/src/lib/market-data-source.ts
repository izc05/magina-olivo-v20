import { apiFetch } from './api-client';
import type { MarketSeries, OliveMarketSnapshot, OliveOilCategory } from './market-data';

export type MarketSnapshotOrigin = 'api' | 'fallback';

export type OliveMarketHistory = {
  revision: string;
  sourcePublishedOn: string;
  windowWeeks: number;
  availableFrom: string;
  availableThrough: string;
  series: MarketSeries[];
};

type ApiMarketPoint = {
  week: number;
  label: string;
  priceEurKg: number;
};

type ApiMarketSeries = {
  id: OliveOilCategory;
  name: string;
  shortName: string;
  description: string;
  points: ApiMarketPoint[];
};

type ApiMarketResponse = {
  market: {
    schemaVersion: number;
    revision: string;
    source: {
      name: string;
      url: string;
      marketLevel: string;
      publishedOn: string;
      validatedThrough: string;
    };
    period: {
      label: string;
    };
    note: string;
    series: ApiMarketSeries[];
  };
};

type ApiMarketHistoryResponse = {
  history: {
    schemaVersion: number;
    origin: 'database' | 'bootstrap';
    revision: string;
    source: {
      publishedOn: string;
    };
    windowWeeks: number;
    availableFrom: string;
    availableThrough: string;
    series: ApiMarketSeries[];
  };
};

function isFinitePrice(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function validateSeries(series: ApiMarketSeries[]): void {
  if (!Array.isArray(series) || series.length !== 3) {
    throw new Error('market_series_invalid');
  }

  for (const entry of series) {
    if (!entry.id || !entry.shortName || !Array.isArray(entry.points) || entry.points.length === 0) {
      throw new Error('market_series_invalid');
    }
    if (entry.points.some((point) => !isFinitePrice(point.priceEurKg))) {
      throw new Error('market_price_invalid');
    }
  }
}

function normalizeSeries(series: ApiMarketSeries[]): MarketSeries[] {
  return series.map((entry) => ({
    id: entry.id,
    name: entry.name,
    shortName: entry.shortName,
    description: entry.description,
    points: entry.points.map((point) => ({
      week: point.week,
      label: point.label,
      priceEurKg: point.priceEurKg,
    })),
  }));
}

function validateResponse(payload: ApiMarketResponse): OliveMarketSnapshot {
  const market = payload?.market;
  if (!market || market.schemaVersion !== 1 || typeof market.revision !== 'string') {
    throw new Error('market_snapshot_invalid');
  }

  validateSeries(market.series);

  return {
    revision: market.revision,
    sourceName: market.source.name,
    sourceUrl: market.source.url,
    sourcePublishedOn: market.source.publishedOn,
    marketLevel: market.source.marketLevel,
    periodLabel: market.period.label,
    validatedThrough: market.source.validatedThrough,
    note: market.note,
    series: normalizeSeries(market.series),
  };
}

function validateHistoryResponse(payload: ApiMarketHistoryResponse): OliveMarketHistory {
  const history = payload?.history;
  if (
    !history ||
    history.schemaVersion !== 1 ||
    typeof history.revision !== 'string' ||
    !Number.isInteger(history.windowWeeks) ||
    history.windowWeeks < 1 ||
    history.windowWeeks > 52
  ) {
    throw new Error('market_history_invalid');
  }

  validateSeries(history.series);

  if (history.series.some((series) => series.points.length !== history.windowWeeks)) {
    throw new Error('market_history_window_invalid');
  }

  return {
    revision: history.revision,
    sourcePublishedOn: history.source.publishedOn,
    windowWeeks: history.windowWeeks,
    availableFrom: history.availableFrom,
    availableThrough: history.availableThrough,
    series: normalizeSeries(history.series),
  };
}

export async function fetchOliveMarketSnapshot(): Promise<OliveMarketSnapshot> {
  const response = await apiFetch<ApiMarketResponse>('/api/v1/public/market/olive-oil');
  return validateResponse(response);
}

export async function fetchOliveMarketHistory(weeks: number): Promise<OliveMarketHistory> {
  if (!Number.isInteger(weeks) || weeks < 1 || weeks > 52) {
    throw new Error('market_history_weeks_invalid');
  }

  const response = await apiFetch<ApiMarketHistoryResponse>(
    `/api/v1/public/market/olive-oil/history?weeks=${weeks}`,
  );
  return validateHistoryResponse(response);
}
