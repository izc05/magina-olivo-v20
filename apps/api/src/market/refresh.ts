import { createHash } from 'node:crypto';
import type { DatabaseClient } from '../db/client.js';
import {
  loadPersistedOliveOilMarketHistory,
  snapshotFromOliveOilMarketHistory,
  upsertOliveOilMarketSnapshot,
} from './history.js';
import { fetchJuntaOliveOilMarketSnapshot } from './junta-observatorio-adapter.js';
import type { OliveOilMarketSnapshot } from './snapshot.js';

export type MarketRefreshKind = 'initial' | 'unchanged' | 'new-period' | 'correction';

export type MarketRefreshPlan = {
  kind: MarketRefreshKind;
  currentRevision: string | null;
  candidateRevision: string;
  currentThrough: string | null;
  candidateThrough: string;
  snapshot: OliveOilMarketSnapshot;
};

function candidateFingerprint(snapshot: OliveOilMarketSnapshot): string {
  const payload = snapshot.series
    .map((series) => ({
      id: series.id,
      points: series.points.map((point) => [point.periodStart, point.periodEnd, point.priceEurKg]),
    }))
    .sort((left, right) => left.id.localeCompare(right.id));
  return createHash('sha256').update(JSON.stringify(payload)).digest('hex').slice(0, 10);
}

function comparablePoints(snapshot: OliveOilMarketSnapshot): Map<string, number> {
  const values = new Map<string, number>();
  for (const series of snapshot.series) {
    for (const point of series.points) {
      values.set(`${series.id}:${point.periodStart}:${point.periodEnd}`, point.priceEurKg);
    }
  }
  return values;
}

function candidateMatchesCurrent(current: OliveOilMarketSnapshot, candidate: OliveOilMarketSnapshot): boolean {
  const currentValues = comparablePoints(current);
  for (const series of candidate.series) {
    for (const point of series.points) {
      const key = `${series.id}:${point.periodStart}:${point.periodEnd}`;
      if (currentValues.get(key) !== point.priceEurKg) return false;
    }
  }
  return true;
}

function correctionRevision(snapshot: OliveOilMarketSnapshot): string {
  const base = snapshot.revision.replace(/-corr-[a-f0-9]+$/i, '');
  return `${base}-corr-${candidateFingerprint(snapshot)}`;
}

export function validateMarketSourceClock(snapshot: OliveOilMarketSnapshot, now = new Date()): void {
  const publishedAt = Date.parse(`${snapshot.source.publishedOn}T00:00:00Z`);
  const validatedThrough = Date.parse(`${snapshot.source.validatedThrough}T00:00:00Z`);
  const currentDay = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  if (!Number.isFinite(publishedAt) || !Number.isFinite(validatedThrough)) {
    throw new Error('market_source_clock_invalid');
  }
  if (validatedThrough > publishedAt) throw new Error('market_source_validated_after_publication');
  if (publishedAt > currentDay + 86_400_000) throw new Error('market_source_publication_in_future');
}

export function planOliveOilMarketRefresh(
  current: OliveOilMarketSnapshot | null,
  candidate: OliveOilMarketSnapshot,
  now = new Date(),
): MarketRefreshPlan {
  validateMarketSourceClock(candidate, now);

  if (!current) {
    return {
      kind: 'initial',
      currentRevision: null,
      candidateRevision: candidate.revision,
      currentThrough: null,
      candidateThrough: candidate.period.end,
      snapshot: candidate,
    };
  }

  if (candidate.period.end < current.period.end) {
    throw new Error(`market_source_regression:${candidate.period.end}:${current.period.end}`);
  }

  if (candidate.period.end > current.period.end) {
    return {
      kind: 'new-period',
      currentRevision: current.revision,
      candidateRevision: candidate.revision,
      currentThrough: current.period.end,
      candidateThrough: candidate.period.end,
      snapshot: candidate,
    };
  }

  if (candidateMatchesCurrent(current, candidate)) {
    return {
      kind: 'unchanged',
      currentRevision: current.revision,
      candidateRevision: current.revision,
      currentThrough: current.period.end,
      candidateThrough: candidate.period.end,
      snapshot: current,
    };
  }

  const corrected = { ...candidate, revision: correctionRevision(candidate) };
  return {
    kind: 'correction',
    currentRevision: current.revision,
    candidateRevision: corrected.revision,
    currentThrough: current.period.end,
    candidateThrough: corrected.period.end,
    snapshot: corrected,
  };
}

export async function refreshOliveOilMarketFromJunta(
  db: DatabaseClient,
  options: { apply?: boolean; now?: Date } = {},
): Promise<MarketRefreshPlan & { applied: boolean }> {
  const candidate = await fetchJuntaOliveOilMarketSnapshot();
  const currentHistory = await loadPersistedOliveOilMarketHistory(db, 52);
  const current = currentHistory ? snapshotFromOliveOilMarketHistory(currentHistory) : null;
  const plan = planOliveOilMarketRefresh(current, candidate, options.now);
  const shouldApply = options.apply === true && plan.kind !== 'unchanged';

  if (shouldApply) await upsertOliveOilMarketSnapshot(db, plan.snapshot);
  return { ...plan, applied: shouldApply };
}
