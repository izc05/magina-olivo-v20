import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import {
  oliveOilMarketSnapshot,
  type OliveOilMarketCategory,
  type OliveOilMarketPoint,
  type OliveOilMarketSeries,
  type OliveOilMarketSnapshot,
} from './snapshot.js';

export const oliveOilMarketSourceKey = 'junta-andalucia-observatorio';

export type OliveOilMarketHistory = {
  schemaVersion: 1;
  origin: 'database' | 'bootstrap';
  revision: string;
  source: OliveOilMarketSnapshot['source'];
  windowWeeks: number;
  availableFrom: string;
  availableThrough: string;
  series: OliveOilMarketSeries[];
};

type PersistedMarketRow = {
  category: OliveOilMarketCategory;
  period_week: number;
  period_start: string | Date;
  period_end: string | Date;
  price_eur_kg: string | number;
  revision: string;
  snapshot_published_on: string | Date;
  validated_through: string | Date;
  source_name: string;
  source_url: string;
  market_level: string;
};

const categoryOrder: OliveOilMarketCategory[] = ['virgen-extra', 'virgen', 'lampante'];
const monthLabels = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'] as const;

function asIsoDate(value: string | Date): string {
  if (value instanceof Date) return value.toISOString().slice(0, 10);
  return String(value).slice(0, 10);
}

function pointLabel(periodStart: string, periodEnd: string): string {
  const start = new Date(`${periodStart}T00:00:00Z`);
  const end = new Date(`${periodEnd}T00:00:00Z`);
  const startDay = start.getUTCDate();
  const endDay = end.getUTCDate();
  const startMonth = monthLabels[start.getUTCMonth()] ?? '';
  const endMonth = monthLabels[end.getUTCMonth()] ?? '';

  if (start.getUTCMonth() === end.getUTCMonth()) return `${startDay}–${endDay} ${endMonth}`;
  return `${startDay} ${startMonth}–${endDay} ${endMonth}`;
}

function buildSeries(
  category: OliveOilMarketCategory,
  points: OliveOilMarketPoint[],
): OliveOilMarketSeries {
  const metadata = oliveOilMarketSnapshot.series.find((item) => item.id === category);
  if (!metadata) throw new Error(`market_category_metadata_missing:${category}`);

  const latestPrice = points.at(-1)?.priceEurKg ?? 0;
  const previousPrice = points.at(-2)?.priceEurKg ?? latestPrice;
  const delta = latestPrice - previousPrice;

  return {
    id: category,
    name: metadata.name,
    shortName: metadata.shortName,
    description: metadata.description,
    unit: metadata.unit,
    points,
    latest: {
      priceEurKg: latestPrice,
      previousPriceEurKg: previousPrice,
      deltaEurKg: Number(delta.toFixed(4)),
      deltaPercent: previousPrice > 0 ? Number(((delta / previousPrice) * 100).toFixed(2)) : 0,
    },
  };
}

export function bootstrapOliveOilMarketHistory(weeks = 8): OliveOilMarketHistory {
  const safeWeeks = Math.max(1, Math.min(52, Math.trunc(weeks)));
  const series = oliveOilMarketSnapshot.series.map((item) =>
    buildSeries(item.id, item.points.slice(-safeWeeks)),
  );
  const points = series[0]?.points ?? [];

  return {
    schemaVersion: 1,
    origin: 'bootstrap',
    revision: oliveOilMarketSnapshot.revision,
    source: oliveOilMarketSnapshot.source,
    windowWeeks: points.length,
    availableFrom: points[0]?.periodStart ?? oliveOilMarketSnapshot.period.start,
    availableThrough: points.at(-1)?.periodEnd ?? oliveOilMarketSnapshot.period.end,
    series,
  };
}

export async function loadPersistedOliveOilMarketHistory(
  db: DatabaseClient,
  weeks = 8,
): Promise<OliveOilMarketHistory | null> {
  const safeWeeks = Math.max(1, Math.min(52, Math.trunc(weeks)));
  const result = await sql<PersistedMarketRow>`
    WITH recent_periods AS (
      SELECT period_start, MAX(period_end) AS period_end
      FROM market_olive_oil_weekly
      WHERE source_key = ${oliveOilMarketSourceKey}
        AND status = 'validated'
      GROUP BY period_start
      ORDER BY MAX(period_end) DESC
      LIMIT ${safeWeeks}
    )
    SELECT
      market.category,
      market.period_week,
      market.period_start,
      market.period_end,
      market.price_eur_kg,
      market.revision,
      market.snapshot_published_on,
      market.validated_through,
      market.source_name,
      market.source_url,
      market.market_level
    FROM market_olive_oil_weekly market
    JOIN recent_periods recent ON recent.period_start = market.period_start
    WHERE market.source_key = ${oliveOilMarketSourceKey}
      AND market.status = 'validated'
    ORDER BY
      market.period_end ASC,
      CASE market.category
        WHEN 'virgen-extra' THEN 1
        WHEN 'virgen' THEN 2
        WHEN 'lampante' THEN 3
        ELSE 4
      END ASC
  `.execute(db);

  if (result.rows.length === 0) return null;

  const rowsByCategory = new Map<OliveOilMarketCategory, PersistedMarketRow[]>();
  for (const category of categoryOrder) rowsByCategory.set(category, []);
  for (const row of result.rows) rowsByCategory.get(row.category)?.push(row);

  const expectedCount = Math.min(safeWeeks, rowsByCategory.get('virgen-extra')?.length ?? 0);
  if (expectedCount === 0) return null;
  if (categoryOrder.some((category) => rowsByCategory.get(category)?.length !== expectedCount)) return null;

  const periodSignatures = categoryOrder.map((category) =>
    (rowsByCategory.get(category) ?? []).map((row) => asIsoDate(row.period_start)).join('|'),
  );
  if (!periodSignatures.every((signature) => signature === periodSignatures[0])) return null;

  const series = categoryOrder.map((category) => {
    const points = (rowsByCategory.get(category) ?? []).map<OliveOilMarketPoint>((row) => {
      const periodStart = asIsoDate(row.period_start);
      const periodEnd = asIsoDate(row.period_end);
      return {
        week: Number(row.period_week),
        periodStart,
        periodEnd,
        label: pointLabel(periodStart, periodEnd),
        priceEurKg: Number(row.price_eur_kg),
      };
    });
    return buildSeries(category, points);
  });

  const latestRow = [...result.rows].sort((left, right) =>
    asIsoDate(right.period_end).localeCompare(asIsoDate(left.period_end)),
  )[0];
  const firstPoint = series[0]?.points[0];
  const lastPoint = series[0]?.points.at(-1);
  if (!latestRow || !firstPoint || !lastPoint) return null;

  return {
    schemaVersion: 1,
    origin: 'database',
    revision: latestRow.revision,
    source: {
      name: latestRow.source_name,
      url: latestRow.source_url,
      marketLevel: latestRow.market_level,
      publishedOn: asIsoDate(latestRow.snapshot_published_on),
      validatedThrough: asIsoDate(latestRow.validated_through),
    },
    windowWeeks: expectedCount,
    availableFrom: firstPoint.periodStart,
    availableThrough: lastPoint.periodEnd,
    series,
  };
}

export function snapshotFromOliveOilMarketHistory(history: OliveOilMarketHistory): OliveOilMarketSnapshot {
  const latestPoint = history.series[0]?.points.at(-1);
  if (!latestPoint) return oliveOilMarketSnapshot;
  const latestYear = Number(latestPoint.periodEnd.slice(0, 4));

  return {
    schemaVersion: 1,
    revision: history.revision,
    source: history.source,
    period: {
      week: latestPoint.week,
      start: latestPoint.periodStart,
      end: latestPoint.periodEnd,
      label: `Semana ${latestPoint.week} · ${latestPoint.label} ${latestYear}`,
    },
    note: oliveOilMarketSnapshot.note,
    series: history.series,
  };
}

/**
 * Idempotent write path for the future source adapter. A repeated ingest of the
 * same publication updates corrected values and source metadata in place.
 */
export async function upsertOliveOilMarketSnapshot(
  db: DatabaseClient,
  snapshot: OliveOilMarketSnapshot,
): Promise<void> {
  await db.transaction().execute(async (transaction) => {
    for (const series of snapshot.series) {
      for (const point of series.points) {
        await sql`
          INSERT INTO market_olive_oil_weekly (
            source_key,
            category,
            period_week,
            period_start,
            period_end,
            price_eur_kg,
            revision,
            snapshot_published_on,
            validated_through,
            source_name,
            source_url,
            market_level,
            status
          )
          VALUES (
            ${oliveOilMarketSourceKey},
            ${series.id},
            ${point.week},
            ${point.periodStart}::date,
            ${point.periodEnd}::date,
            ${point.priceEurKg},
            ${snapshot.revision},
            ${snapshot.source.publishedOn}::date,
            ${snapshot.source.validatedThrough}::date,
            ${snapshot.source.name},
            ${snapshot.source.url},
            ${snapshot.source.marketLevel},
            'validated'
          )
          ON CONFLICT (source_key, category, period_start)
          DO UPDATE SET
            period_week = EXCLUDED.period_week,
            period_end = EXCLUDED.period_end,
            price_eur_kg = EXCLUDED.price_eur_kg,
            revision = EXCLUDED.revision,
            snapshot_published_on = EXCLUDED.snapshot_published_on,
            validated_through = EXCLUDED.validated_through,
            source_name = EXCLUDED.source_name,
            source_url = EXCLUDED.source_url,
            market_level = EXCLUDED.market_level,
            status = EXCLUDED.status,
            ingested_at = now()
        `.execute(transaction);
      }
    }
  });
}
