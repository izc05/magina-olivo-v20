import type { FastifyInstance } from 'fastify';
import { oliveOilMarketEtag, oliveOilMarketSnapshot } from '../market/snapshot.js';

export function registerMarketRoutes(app: FastifyInstance) {
  app.get('/api/v1/public/market/olive-oil', async (request, reply) => {
    reply.header('cache-control', 'public, max-age=900, stale-while-revalidate=3600');
    reply.header('etag', oliveOilMarketEtag);
    reply.header('last-modified', 'Wed, 09 Sep 2026 00:00:00 GMT');

    if (request.headers['if-none-match'] === oliveOilMarketEtag) {
      return reply.code(304).send();
    }

    return { market: oliveOilMarketSnapshot };
  });
}
