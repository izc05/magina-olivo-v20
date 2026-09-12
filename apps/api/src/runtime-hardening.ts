import type { FastifyReply, FastifyRequest } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from './db/client.js';

type RateLimitClass = 'auth' | 'public' | 'private';

type RateLimitConfig = {
  enabled: boolean;
  windowMs: number;
  authMax: number;
  publicMax: number;
  privateMax: number;
  maxBuckets: number;
};

type Bucket = {
  count: number;
  resetAt: number;
};

function positiveInteger(value: string | undefined, fallback: number) {
  if (!value) return fallback;
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function runtimeRateLimitConfig(): RateLimitConfig {
  const productionDefault = process.env.NODE_ENV === 'production';
  const configuredEnabled = process.env.RATE_LIMIT_ENABLED?.trim().toLowerCase();
  const enabled = configuredEnabled === 'true'
    ? true
    : configuredEnabled === 'false'
      ? false
      : productionDefault;

  return {
    enabled,
    windowMs: positiveInteger(process.env.RATE_LIMIT_WINDOW_MS, 60_000),
    authMax: positiveInteger(process.env.RATE_LIMIT_AUTH_MAX, 20),
    publicMax: positiveInteger(process.env.RATE_LIMIT_PUBLIC_MAX, 120),
    privateMax: positiveInteger(process.env.RATE_LIMIT_PRIVATE_MAX, 300),
    maxBuckets: positiveInteger(process.env.RATE_LIMIT_MAX_BUCKETS, 10_000),
  };
}

function requestPath(request: FastifyRequest) {
  return request.url.split('?', 1)[0] ?? request.url;
}

function classifyRequest(request: FastifyRequest): RateLimitClass | null {
  if (request.method === 'OPTIONS') return null;
  const path = requestPath(request);
  if (path === '/health' || path === '/ready') return null;
  if (path.startsWith('/api/v1/auth/')) return 'auth';
  if (path.startsWith('/api/v1/public/')) return 'public';
  if (path.startsWith('/api/v1/')) return 'private';
  return null;
}

function limitFor(kind: RateLimitClass, config: RateLimitConfig) {
  if (kind === 'auth') return config.authMax;
  if (kind === 'public') return config.publicMax;
  return config.privateMax;
}

export function createRuntimeRateLimitHook(config: RateLimitConfig = runtimeRateLimitConfig()) {
  const buckets = new Map<string, Bucket>();
  let operations = 0;

  function prune(now: number) {
    for (const [key, bucket] of buckets) {
      if (bucket.resetAt <= now) buckets.delete(key);
    }
    while (buckets.size > config.maxBuckets) {
      const oldest = buckets.keys().next();
      if (oldest.done) break;
      buckets.delete(oldest.value);
    }
  }

  return async function runtimeRateLimitHook(request: FastifyRequest, reply: FastifyReply) {
    if (!config.enabled) return;
    const kind = classifyRequest(request);
    if (!kind) return;

    const now = Date.now();
    operations += 1;
    if (operations % 256 === 0 || buckets.size > config.maxBuckets) prune(now);

    const limit = limitFor(kind, config);
    const key = `${kind}:${request.ip}`;
    const previous = buckets.get(key);
    const bucket = !previous || previous.resetAt <= now
      ? { count: 0, resetAt: now + config.windowMs }
      : previous;

    bucket.count += 1;
    buckets.set(key, bucket);

    const remaining = Math.max(0, limit - bucket.count);
    const resetEpochSeconds = Math.ceil(bucket.resetAt / 1000);
    const retryAfterSeconds = Math.max(1, Math.ceil((bucket.resetAt - now) / 1000));

    reply.header('x-ratelimit-limit', String(limit));
    reply.header('x-ratelimit-remaining', String(remaining));
    reply.header('x-ratelimit-reset', String(resetEpochSeconds));

    if (bucket.count <= limit) return;

    reply.header('retry-after', String(retryAfterSeconds));
    request.log.warn({ rateLimitClass: kind, limit }, 'rate limit exceeded');
    return reply.code(429).send({
      error: 'rate_limited',
      retry_after_seconds: retryAfterSeconds,
      request_id: request.id,
    });
  };
}

export type ReadinessStatus = {
  ok: boolean;
  service: 'magina-api';
  database: 'ready' | 'unconfigured' | 'unavailable';
  latencyMs?: number;
};

export async function checkRuntimeReadiness(db: DatabaseClient | null): Promise<ReadinessStatus> {
  if (!db) {
    return { ok: false, service: 'magina-api', database: 'unconfigured' };
  }

  const startedAt = performance.now();
  try {
    await sql`SELECT 1 AS ready`.execute(db);
    return {
      ok: true,
      service: 'magina-api',
      database: 'ready',
      latencyMs: Math.max(0, Math.round((performance.now() - startedAt) * 10) / 10),
    };
  } catch {
    return {
      ok: false,
      service: 'magina-api',
      database: 'unavailable',
    };
  }
}
