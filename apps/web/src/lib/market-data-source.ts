import { apiFetch } from './api-client';
import type { MarketSeries, OliveMarketSnapshot, OliveOilCategory } from './market-data';

export type MarketSnapshotOrigin = 'api' | 'fallback';

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

function isFinitePrice(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
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

  if (!Array.isArray(market.series) || market.series.length !== 3) {
    throw new Error('market_series_invalid');
  }

  for (const series of market.series) {
    if (!series.id || !series.shortName || !Array.isArray(series.points) || series.points.length === 0) {
      throw new Error('market_series_invalid');
    }
    if (series.points.some((point) => !isFinitePrice(point.priceEurKg))) {
      throw new Error('market_price_invalid');
    }
  }

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

export async function fetchOliveMarketSnapshot(): Promise<OliveMarketSnapshot> {
  const response = await apiFetch<ApiMarketResponse>('/api/v1/public/market/olive-oil');
  return validateResponse(response);
}
