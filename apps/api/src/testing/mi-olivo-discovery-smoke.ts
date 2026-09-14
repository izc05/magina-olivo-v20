import assert from 'node:assert/strict';
import { sql } from 'kysely';
import { buildApp } from '../app.js';
import { createDatabase } from '../db/client.js';
import type { GoogleIdentityClaims, GoogleIdentityVerifier } from '../auth/google.js';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL is required for Mi Olivo Discovery smoke test.');

const db = createDatabase(databaseUrl);
const googleClaims: GoogleIdentityClaims = {
  subject: 'google-mi-olivo-discovery-ci-0001',
  email: 'mi.olivo.discovery.ci@example.test',
  emailVerified: true,
  displayName: 'Explorador Mi Olivo CI',
  pictureUrl: null,
  givenName: 'Explorador',
  familyName: 'CI',
  hostedDomain: null,
};
const googleVerifier: GoogleIdentityVerifier = { async verify() { return googleClaims; } };
const app = buildApp({ db, googleVerifier });

async function award(headers: Record<string, string>, eventType: string, sourceId: string) {
  return app.inject({
    method: 'POST',
    url: '/api/v1/mi-olivo/discovery-events',
    headers,
    payload: { event_type: eventType, source_id: sourceId },
  });
}

try {
  await app.ready();
  const credential = 'synthetic-google-discovery-token-'.padEnd(140, 'd');
  const login = await app.inject({ method: 'POST', url: '/api/v1/auth/google', payload: { credential } });
  assert.equal(login.statusCode, 201, login.body);
  const loginBody = login.json();
  const userId = String(loginBody.user.id);
  const workspaceId = String(loginBody.workspaces[0].workspace_id);
  const cookie = String(login.headers['set-cookie']).split(';', 1)[0];
  const headers = { cookie, 'x-workspace-id': workspaceId };

  const section = await award(headers, 'section_discovered', 'section:almazaras');
  assert.equal(section.statusCode, 200, section.body);
  assert.equal(section.json().awarded, true);
  assert.equal(section.json().points, 2);
  assert.equal(section.json().daily.cap, 30);

  const duplicateSection = await award(headers, 'section_discovered', 'section:almazaras');
  assert.equal(duplicateSection.statusCode, 200, duplicateSection.body);
  assert.equal(duplicateSection.json().awarded, false);
  assert.equal(duplicateSection.json().status, 'already_recognized');

  const mill = await award(headers, 'mill_discovered', 'mill:almazara-bedmar');
  assert.equal(mill.statusCode, 200, mill.body);
  assert.equal(mill.json().points, 5);

  const business = await award(headers, 'business_discovered', 'business:comercio-local');
  assert.equal(business.statusCode, 200, business.body);
  assert.equal(business.json().points, 4);

  const experience = await award(headers, 'experience_discovered', 'experience:almazara-bedmar:cata-premium');
  assert.equal(experience.statusCode, 200, experience.body);
  assert.equal(experience.json().points, 6);

  const market = await award(headers, 'market_checked', 'market');
  assert.equal(market.statusCode, 200, market.body);
  assert.equal(market.json().points, 2);

  const duplicateMarket = await award(headers, 'market_checked', 'market');
  assert.equal(duplicateMarket.statusCode, 200, duplicateMarket.body);
  assert.equal(duplicateMarket.json().awarded, false);
  assert.equal(duplicateMarket.json().status, 'already_recognized');

  const route = await award(headers, 'route_discovered', 'route:sendero-cuadros');
  assert.equal(route.statusCode, 200, route.body);
  assert.equal(route.json().points, 8);
  assert.equal(route.json().daily.earned, 27);

  const overCap = await award(headers, 'heritage_discovered', 'heritage:castillo-bedmar');
  assert.equal(overCap.statusCode, 200, overCap.body);
  assert.equal(overCap.json().awarded, false);
  assert.equal(overCap.json().status, 'daily_cap');
  assert.equal(overCap.json().daily.earned, 27);

  const invalidSource = await award(headers, 'business_discovered', 'fake-source');
  assert.equal(invalidSource.statusCode, 400, invalidSource.body);

  const invalidSection = await award(headers, 'section_discovered', 'section:no-existe');
  assert.equal(invalidSection.statusCode, 400, invalidSection.body);

  const summary = await app.inject({ method: 'GET', url: '/api/v1/mi-olivo/discovery-summary', headers });
  assert.equal(summary.statusCode, 200, summary.body);
  const summaryBody = summary.json();
  assert.equal(summaryBody.rule_version, 'mi-olivo-v3');
  assert.equal(summaryBody.points, 27);
  assert.equal(summaryBody.discoveries, 5);
  assert.equal(summaryBody.worlds_discovered, 6);
  assert.equal(summaryBody.counts.sections, 1);
  assert.equal(summaryBody.counts.mills, 1);
  assert.equal(summaryBody.counts.businesses, 1);
  assert.equal(summaryBody.counts.experiences, 1);
  assert.equal(summaryBody.counts.routes, 1);
  assert.equal(summaryBody.counts.heritage, 0);
  assert.equal(summaryBody.counts.market_checks, 1);
  assert.equal(summaryBody.today.earned, 27);
  assert.equal(summaryBody.today.cap, 30);
  assert.equal(summaryBody.weekly.earned, 27);
  assert.equal(summaryBody.weekly.goal, 60);
  assert.ok(summaryBody.rhythm.active_weeks >= 1);

  const paused = await app.inject({
    method: 'PUT',
    url: '/api/v1/mi-olivo/preferences',
    headers,
    payload: { enabled: false },
  });
  assert.equal(paused.statusCode, 200, paused.body);
  assert.equal(paused.json().enabled, false);

  const pausedEvent = await award(headers, 'heritage_discovered', 'heritage:castillo-bedmar');
  assert.equal(pausedEvent.statusCode, 200, pausedEvent.body);
  assert.equal(pausedEvent.json().status, 'paused');
  assert.equal(pausedEvent.json().awarded, false);

  const ledger = await sql<{ total: number; points: number; distinct_keys: number }>`
    SELECT
      COUNT(*)::int AS total,
      COALESCE(SUM(points), 0)::int AS points,
      COUNT(DISTINCT idempotency_key)::int AS distinct_keys
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND rule_version = 'mi-olivo-v3'
  `.execute(db);
  assert.equal(ledger.rows[0]?.total, 6);
  assert.equal(ledger.rows[0]?.points, 27);
  assert.equal(ledger.rows[0]?.distinct_keys, 6);

  console.log('MI_OLIVO_DISCOVERY_SMOKE_OK');
} finally {
  await app.close();
  await db.destroy();
}
