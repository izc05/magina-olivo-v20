import type { FastifyInstance } from 'fastify';
import type { DatabaseClient } from '../db/client.js';
import {
  bootstrapOliveOilMarketHistory,
  loadPersistedOliveOilMarketHistory,
  snapshotFromOliveOilMarketHistory,
  type OliveOilMarketHistory,
} from '../market/history.js';

const marketCacheControl = 'public, max-age=900, stale-while-revalidate=3600';

function etag(revision: string, suffix = ''): string {
  return `"${revision}${suffix}"`;
}

function lastModified(publishedOn: string): string {
  return new Date(`${publishedOn}T00:00:00Z`).toUTCString();
}

function parseHistoryWeeks(value: unknown): number | null {
  if (value === undefined) return 8;
  const parsed = typeof value === 'number' ? value : Number(String(value));
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 52) return null;
  return parsed;
}

async function resolveHistory(
  app: FastifyInstance,
  db: DatabaseClient | null,
  weeks: number,
): Promise<OliveOilMarketHistory> {
  if (db) {
    try {
      const persisted = await loadPersistedOliveOilMarketHistory(db, weeks);
      if (persisted) return persisted;
    } catch (error) {
      app.log.warn({ err: error }, 'market_history_database_fallback');
    }
  }

  return bootstrapOliveOilMarketHistory(weeks);
}

function applyCacheHeaders(
  reply: Parameters<Parameters<FastifyInstance['get']>[1]>[1],
  revision: string,
  publishedOn: string,
  suffix = '',
) {
  const responseEtag = etag(revision, suffix);
  reply.header('cache-control', marketCacheControl);
  reply.header('etag', responseEtag);
  reply.header('last-modified', lastModified(publishedOn));
  return responseEtag;
}

export function registerMarketRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/market/olive-oil', async (request, reply) => {
    const history = await resolveHistory(app, db, 8);
    const snapshot = snapshotFromOliveOilMarketHistory(history);
    const responseEtag = applyCacheHeaders(reply, snapshot.revision, snapshot.source.publishedOn);

    if (request.headers['if-none-match'] === responseEtag) {
      return reply.code(304).send();
    }

    return { market: snapshot, origin: history.origin };
  });

  app.get('/api/v1/public/market/olive-oil/history', async (request, reply) => {
    const weeks = parseHistoryWeeks((request.query as { weeks?: unknown } | undefined)?.weeks);
    if (weeks === null) {
      return reply.code(400).send({
        error: 'invalid_market_history_weeks',
        min: 1,
        max: 52,
      });
    }

    const history = await resolveHistory(app, db, weeks);
    const responseEtag = applyCacheHeaders(
      reply,
      history.revision,
      history.source.publishedOn,
      `-history-${history.windowWeeks}`,
    );

    if (request.headers['if-none-match'] === responseEtag) {
      return reply.code(304).send();
    }

    return { history };
  });
}
