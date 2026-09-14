import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

const DISCOVERY_RULE_VERSION = 'mi-olivo-v3';
const DAILY_DISCOVERY_CAP = 30;
const WEEKLY_DISCOVERY_GOAL = 60;

const discoverySchema = z.object({
  event_type: z.enum([
    'section_discovered',
    'mill_discovered',
    'business_discovered',
    'experience_discovered',
    'heritage_discovered',
    'route_discovered',
    'market_checked',
  ]),
  source_id: z.string().trim().min(1).max(200),
});

type DiscoveryType = z.infer<typeof discoverySchema>['event_type'];
type ProfileState = { enabled: boolean };
type CountRow = { total: number };
type WeekRow = { week_start: string };
type CurrentWeekRow = { current_week: string };
type IdRow = { id: string };
type CategoryRow = { event_type: string; total: number };

type DiscoveryRule = {
  points: number;
  sourceType: string;
  reason: string;
  sourcePrefix: string;
  daily: boolean;
};

const discoveryRules: Record<DiscoveryType, DiscoveryRule> = {
  section_discovered: {
    points: 2,
    sourceType: 'discovery_section',
    reason: 'Nueva zona de Mágina Olivo descubierta',
    sourcePrefix: 'section:',
    daily: false,
  },
  mill_discovered: {
    points: 5,
    sourceType: 'olive_mill',
    reason: 'Almazara o cooperativa descubierta',
    sourcePrefix: 'mill:',
    daily: false,
  },
  business_discovered: {
    points: 4,
    sourceType: 'local_business',
    reason: 'Negocio local descubierto',
    sourcePrefix: 'business:',
    daily: false,
  },
  experience_discovered: {
    points: 6,
    sourceType: 'local_experience',
    reason: 'Experiencia de Mágina descubierta',
    sourcePrefix: 'experience:',
    daily: false,
  },
  heritage_discovered: {
    points: 5,
    sourceType: 'heritage_place',
    reason: 'Patrimonio de Mágina descubierto',
    sourcePrefix: 'heritage:',
    daily: false,
  },
  route_discovered: {
    points: 8,
    sourceType: 'hiking_route',
    reason: 'Ruta de Mágina descubierta',
    sourcePrefix: 'route:',
    daily: false,
  },
  market_checked: {
    points: 2,
    sourceType: 'market_surface',
    reason: 'Mercado del aceite consultado',
    sourcePrefix: 'market',
    daily: true,
  },
};

const sectionIds = new Set([
  'almazaras',
  'cooperativas',
  'empresas',
  'experiencias',
  'explorar',
  'pueblos',
  'ayuntamientos',
  'servicios',
]);

function safeToken(value: string) {
  return /^[a-z0-9][a-z0-9:_-]{0,199}$/i.test(value);
}

function validDiscoverySource(eventType: DiscoveryType, sourceId: string) {
  if (!safeToken(sourceId)) return false;
  const rule = discoveryRules[eventType];
  if (eventType === 'market_checked') return sourceId === 'market';
  if (!sourceId.startsWith(rule.sourcePrefix)) return false;

  if (eventType === 'section_discovered') {
    const section = sourceId.slice(rule.sourcePrefix.length);
    return sectionIds.has(section);
  }

  const remainder = sourceId.slice(rule.sourcePrefix.length);
  if (!remainder) return false;
  if (eventType === 'experience_discovered') {
    const parts = remainder.split(':');
    return parts.length === 2 && parts.every(Boolean);
  }
  return !remainder.includes(':');
}

async function ensureProfile(database: DatabaseClient, userId: string) {
  await sql`
    INSERT INTO mi_olivo_profiles (user_id)
    VALUES (${userId}::uuid)
    ON CONFLICT (user_id) DO NOTHING
  `.execute(database);
}

async function profileEnabled(database: DatabaseClient, userId: string) {
  const result = await sql<ProfileState>`
    SELECT enabled
    FROM mi_olivo_profiles
    WHERE user_id = ${userId}::uuid
  `.execute(database);
  return result.rows[0]?.enabled ?? true;
}

async function localDate(database: DatabaseClient) {
  const result = await sql<{ local_date: string }>`
    SELECT to_char(now() AT TIME ZONE 'Europe/Madrid', 'YYYY-MM-DD') AS local_date
  `.execute(database);
  return result.rows[0]?.local_date ?? new Date().toISOString().slice(0, 10);
}

async function dailyDiscoveryPoints(database: DatabaseClient, userId: string) {
  const result = await sql<CountRow>`
    SELECT COALESCE(SUM(points), 0)::int AS total
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND rule_version = ${DISCOVERY_RULE_VERSION}
      AND points > 0
      AND created_at >= (
        date_trunc('day', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid'
      )
  `.execute(database);
  return Math.max(0, result.rows[0]?.total ?? 0);
}

async function weeklyDiscoveryPoints(database: DatabaseClient, userId: string) {
  const result = await sql<CountRow>`
    SELECT COALESCE(SUM(points), 0)::int AS total
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND rule_version = ${DISCOVERY_RULE_VERSION}
      AND points > 0
      AND created_at >= (
        date_trunc('week', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid'
      )
  `.execute(database);
  return Math.max(0, result.rows[0]?.total ?? 0);
}

async function totalDiscoveryPoints(database: DatabaseClient, userId: string) {
  const result = await sql<CountRow>`
    SELECT COALESCE(SUM(points), 0)::int AS total
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND rule_version = ${DISCOVERY_RULE_VERSION}
      AND points > 0
  `.execute(database);
  return Math.max(0, result.rows[0]?.total ?? 0);
}

function shiftWeek(weekStart: string, days: number) {
  const [year, month, day] = weekStart.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function projectGlobalRhythm(weekStarts: string[], currentWeek: string) {
  const active = new Set(weekStarts);
  let cursor = currentWeek;
  let graceActive = false;

  if (!active.has(cursor)) {
    const previousWeek = shiftWeek(cursor, -7);
    if (!active.has(previousWeek)) {
      return {
        active_weeks: 0,
        grace_active: false,
        label: 'Empieza a dejar huella',
        message: 'Explorar, aprender, participar o cuidar tu campo puede mantener vivo tu ritmo.',
      };
    }
    cursor = previousWeek;
    graceActive = true;
  }

  let activeWeeks = 0;
  while (active.has(cursor) && activeWeeks < 104) {
    activeWeeks += 1;
    cursor = shiftWeek(cursor, -7);
  }

  return {
    active_weeks: activeWeeks,
    grace_active: graceActive,
    label: activeWeeks === 1 ? '1 semana conectada con Mágina' : `${activeWeeks} semanas conectadas con Mágina`,
    message: graceActive
      ? 'Tienes una semana de margen: tu historia continúa sin penalizaciones.'
      : 'Cuenta cualquier progreso útil de Mi Olivo, no solo trabajo agrícola.',
  };
}

async function globalRhythm(database: DatabaseClient, userId: string) {
  const weeks = await sql<WeekRow>`
    SELECT DISTINCT to_char(
      date_trunc('week', created_at AT TIME ZONE 'Europe/Madrid'),
      'YYYY-MM-DD'
    ) AS week_start
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND points > 0
    ORDER BY week_start DESC
    LIMIT 104
  `.execute(database);
  const current = await sql<CurrentWeekRow>`
    SELECT to_char(
      date_trunc('week', now() AT TIME ZONE 'Europe/Madrid'),
      'YYYY-MM-DD'
    ) AS current_week
  `.execute(database);
  return projectGlobalRhythm(
    weeks.rows.map((row) => row.week_start),
    current.rows[0]?.current_week ?? new Date().toISOString().slice(0, 10),
  );
}

async function categoryCounts(database: DatabaseClient, userId: string) {
  const rows = await sql<CategoryRow>`
    SELECT event_type, COUNT(*)::int AS total
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND rule_version = ${DISCOVERY_RULE_VERSION}
      AND points > 0
    GROUP BY event_type
  `.execute(database);

  const counts: Record<DiscoveryType, number> = {
    section_discovered: 0,
    mill_discovered: 0,
    business_discovered: 0,
    experience_discovered: 0,
    heritage_discovered: 0,
    route_discovered: 0,
    market_checked: 0,
  };
  for (const row of rows.rows) {
    if (row.event_type in counts) counts[row.event_type as DiscoveryType] = row.total;
  }
  return counts;
}

function idempotencyKey(eventType: DiscoveryType, sourceId: string, date: string) {
  const rule = discoveryRules[eventType];
  return rule.daily
    ? `${DISCOVERY_RULE_VERSION}:${eventType}:${sourceId}:${date}`
    : `${DISCOVERY_RULE_VERSION}:${eventType}:${sourceId}`;
}

export function registerMiOlivoDiscoveryRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/mi-olivo/discovery-events', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const input = parseBody(discoverySchema, request.body, reply);
    if (!input) return;
    if (!validDiscoverySource(input.event_type, input.source_id)) {
      return reply.code(400).send({ error: 'Fuente de descubrimiento no válida.' });
    }

    await ensureProfile(database, context.userId);
    if (!(await profileEnabled(database, context.userId))) {
      const earned = await dailyDiscoveryPoints(database, context.userId);
      return {
        awarded: false,
        points: 0,
        status: 'paused',
        message: 'Mi Olivo está pausado.',
        daily: { earned, cap: DAILY_DISCOVERY_CAP, remaining: Math.max(0, DAILY_DISCOVERY_CAP - earned) },
      };
    }

    const rule = discoveryRules[input.event_type];
    const earned = await dailyDiscoveryPoints(database, context.userId);
    if (earned + rule.points > DAILY_DISCOVERY_CAP) {
      return {
        awarded: false,
        points: 0,
        status: 'daily_cap',
        message: 'Hoy ya has explorado bastante. Tu progreso permanente se conserva y mañana podrás seguir descubriendo.',
        daily: { earned, cap: DAILY_DISCOVERY_CAP, remaining: Math.max(0, DAILY_DISCOVERY_CAP - earned) },
      };
    }

    const date = await localDate(database);
    const inserted = await sql<IdRow>`
      INSERT INTO mi_olivo_ledger (
        user_id, workspace_id, event_type, source_type, source_id,
        points, reason, rule_version, idempotency_key
      ) VALUES (
        ${context.userId}::uuid,
        ${context.workspaceId}::uuid,
        ${input.event_type},
        ${rule.sourceType},
        ${input.source_id},
        ${rule.points},
        ${rule.reason},
        ${DISCOVERY_RULE_VERSION},
        ${idempotencyKey(input.event_type, input.source_id, date)}
      )
      ON CONFLICT (user_id, idempotency_key) DO NOTHING
      RETURNING id::text AS id
    `.execute(database);

    const awarded = inserted.rows.length > 0;
    const nextEarned = awarded ? earned + rule.points : earned;
    return {
      awarded,
      points: awarded ? rule.points : 0,
      status: awarded ? 'awarded' : 'already_recognized',
      message: awarded ? rule.reason : 'Este descubrimiento ya forma parte de tu historia.',
      daily: {
        earned: nextEarned,
        cap: DAILY_DISCOVERY_CAP,
        remaining: Math.max(0, DAILY_DISCOVERY_CAP - nextEarned),
      },
    };
  });

  app.get('/api/v1/mi-olivo/discovery-summary', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    await ensureProfile(database, context.userId);
    const [today, weekly, totalPoints, counts, rhythm] = await Promise.all([
      dailyDiscoveryPoints(database, context.userId),
      weeklyDiscoveryPoints(database, context.userId),
      totalDiscoveryPoints(database, context.userId),
      categoryCounts(database, context.userId),
      globalRhythm(database, context.userId),
    ]);

    const permanentDiscoveries =
      counts.section_discovered +
      counts.mill_discovered +
      counts.business_discovered +
      counts.experience_discovered +
      counts.heritage_discovered +
      counts.route_discovered;
    const worldsDiscovered = [
      counts.section_discovered,
      counts.mill_discovered,
      counts.business_discovered,
      counts.experience_discovered,
      counts.heritage_discovered,
      counts.route_discovered,
      counts.market_checked,
    ].filter((value) => value > 0).length;

    return {
      rule_version: DISCOVERY_RULE_VERSION,
      enabled: await profileEnabled(database, context.userId),
      points: totalPoints,
      discoveries: permanentDiscoveries,
      worlds_discovered: worldsDiscovered,
      counts: {
        sections: counts.section_discovered,
        mills: counts.mill_discovered,
        businesses: counts.business_discovered,
        experiences: counts.experience_discovered,
        heritage: counts.heritage_discovered,
        routes: counts.route_discovered,
        market_checks: counts.market_checked,
      },
      today: {
        earned: today,
        cap: DAILY_DISCOVERY_CAP,
        remaining: Math.max(0, DAILY_DISCOVERY_CAP - today),
      },
      weekly: {
        earned: weekly,
        goal: WEEKLY_DISCOVERY_GOAL,
        percent: Math.min(100, Math.round((weekly / WEEKLY_DISCOVERY_GOAL) * 100)),
      },
      rhythm,
    };
  });
}
