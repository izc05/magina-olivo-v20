import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

const RULE_VERSION = 'mi-olivo-v1';
const ENGAGEMENT_RULE_VERSION = 'mi-olivo-v2';
const DAILY_ENGAGEMENT_CAP = 20;
const WEEKLY_FIELD_REWARD_LIMIT = 5;
const WEEKLY_OLIVE_GOAL = 40;

const preferenceSchema = z.object({ enabled: z.boolean() });
const interactionSchema = z.object({
  event_type: z.enum(['content_read', 'territory_viewed', 'weather_checked', 'learning_completed']),
  source_id: z.string().trim().min(1).max(160),
});

type InteractionType = z.infer<typeof interactionSchema>['event_type'];
type ProfileState = { enabled: boolean };
type BooleanRow = { complete: boolean };
type SourceRow = { source_id: string };
type ActivitySourceRow = { source_id: string; source_type: string };
type CountRow = { total: number };
type BalanceRow = { balance: number };
type EventTypeRow = { event_type: string };
type WeekRow = { week_start: string };
type CurrentWeekRow = { current_week: string };
type LocalClockRow = { local_date: string; week_start: string };
type IdRow = { id: string };
type LedgerRow = {
  id: string;
  event_type: string;
  points: number;
  reason: string;
  created_at: Date | string;
};

type AwardInput = {
  userId: string;
  workspaceId: string | null;
  eventType: string;
  sourceType: string;
  sourceId?: string | null;
  points: number;
  reason: string;
  ruleVersion?: string;
  idempotencyKey?: string;
};

type InteractionRule = {
  points: number;
  sourceType: string;
  reason: string;
};

const interactionRules: Record<InteractionType, InteractionRule> = {
  content_read: {
    points: 2,
    sourceType: 'public_content',
    reason: 'Contenido útil consultado',
  },
  territory_viewed: {
    points: 3,
    sourceType: 'territory_place',
    reason: 'Nuevo rincón de Mágina descubierto',
  },
  weather_checked: {
    points: 2,
    sourceType: 'weather_surface',
    reason: 'Clima revisado antes de organizar el campo',
  },
  learning_completed: {
    points: 5,
    sourceType: 'field_learning',
    reason: 'Guía práctica explorada',
  },
};

async function ensureProfile(database: DatabaseClient, userId: string) {
  await sql`
    INSERT INTO mi_olivo_profiles (user_id)
    VALUES (${userId}::uuid)
    ON CONFLICT (user_id) DO NOTHING
  `.execute(database);
}

async function award(database: DatabaseClient, input: AwardInput) {
  const ruleVersion = input.ruleVersion ?? RULE_VERSION;
  const idempotencyKey = input.idempotencyKey ?? `${ruleVersion}:${input.eventType}`;
  const result = await sql<IdRow>`
    INSERT INTO mi_olivo_ledger (
      user_id, workspace_id, event_type, source_type, source_id,
      points, reason, rule_version, idempotency_key
    ) VALUES (
      ${input.userId}::uuid,
      ${input.workspaceId}::uuid,
      ${input.eventType},
      ${input.sourceType},
      ${input.sourceId ?? null},
      ${input.points},
      ${input.reason},
      ${ruleVersion},
      ${idempotencyKey}
    )
    ON CONFLICT (user_id, idempotency_key) DO NOTHING
    RETURNING id::text AS id
  `.execute(database);

  return result.rows.length > 0;
}

async function getLocalClock(database: DatabaseClient) {
  const result = await sql<LocalClockRow>`
    SELECT
      to_char(now() AT TIME ZONE 'Europe/Madrid', 'YYYY-MM-DD') AS local_date,
      to_char(date_trunc('week', now() AT TIME ZONE 'Europe/Madrid'), 'YYYY-MM-DD') AS week_start
  `.execute(database);

  return result.rows[0] ?? {
    local_date: new Date().toISOString().slice(0, 10),
    week_start: new Date().toISOString().slice(0, 10),
  };
}

async function getDailyEngagement(database: DatabaseClient, userId: string) {
  const result = await sql<CountRow>`
    SELECT COALESCE(SUM(points), 0)::int AS total
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND rule_version = ${ENGAGEMENT_RULE_VERSION}
      AND event_type IN ('content_read', 'territory_viewed', 'weather_checked', 'learning_completed')
      AND points > 0
      AND created_at >= (
        date_trunc('day', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid'
      )
  `.execute(database);

  return Math.max(0, result.rows[0]?.total ?? 0);
}

async function getWeeklyOlives(database: DatabaseClient, userId: string) {
  const result = await sql<CountRow>`
    SELECT COALESCE(SUM(points), 0)::int AS total
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND points > 0
      AND created_at >= (
        date_trunc('week', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid'
      )
  `.execute(database);

  return Math.max(0, result.rows[0]?.total ?? 0);
}

function validInteractionSource(eventType: InteractionType, sourceId: string) {
  const normalized = sourceId.toLocaleLowerCase('es');
  const safeSource = /^[a-z0-9][a-z0-9:_-]{0,159}$/i.test(sourceId);
  if (!safeSource) return false;

  if (eventType === 'content_read') {
    return normalized.startsWith('noticia:') || normalized.startsWith('evento:');
  }
  if (eventType === 'territory_viewed') return normalized.startsWith('pueblo:');
  if (eventType === 'weather_checked') return normalized === 'radar';
  return normalized.startsWith('consejo:');
}

function engagementIdempotencyKey(eventType: InteractionType, sourceId: string, workspaceId: string, localDate: string) {
  if (eventType === 'weather_checked') {
    return `${ENGAGEMENT_RULE_VERSION}:${eventType}:${workspaceId}:${localDate}`;
  }
  return `${ENGAGEMENT_RULE_VERSION}:${eventType}:${sourceId}`;
}

async function reconcileVerifiedEvents(database: DatabaseClient, userId: string, workspaceId: string) {
  const profile = await sql<BooleanRow>`
    SELECT (municipality IS NOT NULL AND public_role IS NOT NULL) AS complete
    FROM user_profiles
    WHERE user_id = ${userId}::uuid
  `.execute(database);
  if (profile.rows[0]?.complete) {
    await award(database, {
      userId,
      workspaceId: null,
      eventType: 'profile_ready',
      sourceType: 'user_profile',
      sourceId: userId,
      points: 20,
      reason: 'Perfil básico preparado',
    });
  }

  const firstActivity = await sql<SourceRow>`
    SELECT source_id
    FROM (
      SELECT id::text AS source_id, created_at FROM irrigation_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, created_at FROM treatment_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, created_at FROM fertilization_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, created_at FROM pruning_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, created_at FROM observation_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, created_at FROM expense_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
    ) activity
    ORDER BY created_at ASC
    LIMIT 1
  `.execute(database);

  if (firstActivity.rows[0]) {
    await award(database, {
      userId,
      workspaceId,
      eventType: 'first_activity',
      sourceType: 'farm_activity',
      sourceId: firstActivity.rows[0].source_id,
      points: 25,
      reason: 'Primera actividad registrada',
    });
  }

  const activityCount = await sql<CountRow>`
    SELECT COUNT(*)::int AS total
    FROM (
      SELECT id FROM irrigation_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id FROM treatment_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id FROM fertilization_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id FROM pruning_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id FROM observation_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id FROM expense_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
    ) activity
  `.execute(database);

  if ((activityCount.rows[0]?.total ?? 0) >= 10) {
    await award(database, {
      userId,
      workspaceId,
      eventType: 'ten_activities',
      sourceType: 'farm_activity_count',
      sourceId: '10',
      points: 50,
      reason: 'Diez actividades organizadas',
    });
  }

  const firstDocument = await sql<SourceRow>`
    SELECT id::text AS source_id
    FROM documents
    WHERE workspace_id = ${workspaceId}::uuid
      AND created_by = ${userId}::uuid
      AND status = 'active'
    ORDER BY created_at ASC
    LIMIT 1
  `.execute(database);
  if (firstDocument.rows[0]) {
    await award(database, {
      userId,
      workspaceId,
      eventType: 'first_document',
      sourceType: 'document',
      sourceId: firstDocument.rows[0].source_id,
      points: 25,
      reason: 'Primer documento organizado',
    });
  }

  const firstHarvest = await sql<SourceRow>`
    SELECT id::text AS source_id
    FROM harvest_deliveries
    WHERE workspace_id = ${workspaceId}::uuid
      AND created_by = ${userId}::uuid
    ORDER BY created_at ASC
    LIMIT 1
  `.execute(database);
  if (firstHarvest.rows[0]) {
    await award(database, {
      userId,
      workspaceId,
      eventType: 'first_harvest_delivery',
      sourceType: 'harvest_delivery',
      sourceId: firstHarvest.rows[0].source_id,
      points: 30,
      reason: 'Primera entrega de cosecha registrada',
    });
  }
}

async function reconcileWeeklyFieldRewards(database: DatabaseClient, userId: string, workspaceId: string) {
  const alreadyRewardedResult = await sql<CountRow>`
    SELECT COUNT(*)::int AS total
    FROM mi_olivo_ledger
    WHERE user_id = ${userId}::uuid
      AND workspace_id = ${workspaceId}::uuid
      AND event_type = 'field_activity_reward'
      AND rule_version = ${ENGAGEMENT_RULE_VERSION}
      AND points > 0
      AND created_at >= (
        date_trunc('week', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid'
      )
  `.execute(database);

  let rewarded = alreadyRewardedResult.rows[0]?.total ?? 0;
  if (rewarded >= WEEKLY_FIELD_REWARD_LIMIT) return;

  const candidates = await sql<ActivitySourceRow>`
    SELECT source_id, source_type
    FROM (
      SELECT id::text AS source_id, 'irrigation_record' AS source_type, created_at FROM irrigation_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, 'treatment_record' AS source_type, created_at FROM treatment_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, 'fertilization_record' AS source_type, created_at FROM fertilization_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, 'pruning_record' AS source_type, created_at FROM pruning_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, 'observation_record' AS source_type, created_at FROM observation_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
      UNION ALL
      SELECT id::text AS source_id, 'expense_record' AS source_type, created_at FROM expense_records WHERE workspace_id = ${workspaceId}::uuid AND created_by = ${userId}::uuid
    ) activity
    WHERE created_at >= (
      date_trunc('week', now() AT TIME ZONE 'Europe/Madrid') AT TIME ZONE 'Europe/Madrid'
    )
    ORDER BY created_at ASC
    LIMIT 50
  `.execute(database);

  for (const candidate of candidates.rows) {
    if (rewarded >= WEEKLY_FIELD_REWARD_LIMIT) break;
    const inserted = await award(database, {
      userId,
      workspaceId,
      eventType: 'field_activity_reward',
      sourceType: candidate.source_type,
      sourceId: candidate.source_id,
      points: 4,
      reason: 'Trabajo real registrado esta semana',
      ruleVersion: ENGAGEMENT_RULE_VERSION,
      idempotencyKey: `${ENGAGEMENT_RULE_VERSION}:field_activity:${candidate.source_type}:${candidate.source_id}`,
    });
    if (inserted) rewarded += 1;
  }
}

function levelLabel(level: number) {
  if (level >= 5) return 'Olivo maestro';
  if (level === 4) return 'Olivo arraigado';
  if (level === 3) return 'Olivo joven';
  if (level === 2) return 'Rama nueva';
  return 'Brote';
}

function shiftWeek(weekStart: string, days: number) {
  const [year, month, day] = weekStart.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function projectRhythm(weekStarts: string[], currentWeek: string) {
  const active = new Set(weekStarts);
  let cursor = currentWeek;
  let graceActive = false;

  if (!active.has(cursor)) {
    const previousWeek = shiftWeek(cursor, -7);
    if (!active.has(previousWeek)) {
      return {
        active_weeks: 0,
        grace_active: false,
        label: 'Sin ritmo activo',
        message: 'Se activa cuando registras una actividad útil. No pierdes puntos por descansar.',
      };
    }
    cursor = previousWeek;
    graceActive = true;
  }

  let activeWeeks = 0;
  while (active.has(cursor) && activeWeeks < 53) {
    activeWeeks += 1;
    cursor = shiftWeek(cursor, -7);
  }

  return {
    active_weeks: activeWeeks,
    grace_active: graceActive,
    label: activeWeeks === 1 ? '1 semana activa' : `${activeWeeks} semanas activas`,
    message: graceActive
      ? 'Esta semana tiene margen: tu ritmo continúa sin penalización.'
      : 'Cuenta semanas con actividad real registrada, sin premiar aperturas de la app.',
  };
}

export function registerMiOlivoRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/mi-olivo', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    await ensureProfile(database, context.userId);

    const profile = await sql<ProfileState>`
      SELECT enabled
      FROM mi_olivo_profiles
      WHERE user_id = ${context.userId}::uuid
    `.execute(database);
    const enabled = profile.rows[0]?.enabled ?? true;

    if (enabled) {
      await reconcileVerifiedEvents(database, context.userId, context.workspaceId);
      await reconcileWeeklyFieldRewards(database, context.userId, context.workspaceId);
    }

    const balanceResult = await sql<BalanceRow>`
      SELECT COALESCE(SUM(points), 0)::int AS balance
      FROM mi_olivo_ledger
      WHERE user_id = ${context.userId}::uuid
    `.execute(database);
    const balance = Math.max(0, balanceResult.rows[0]?.balance ?? 0);
    const level = Math.floor(balance / 100) + 1;
    const levelFloor = (level - 1) * 100;
    const progress = balance - levelFloor;

    const eventResult = await sql<EventTypeRow>`
      SELECT DISTINCT event_type
      FROM mi_olivo_ledger
      WHERE user_id = ${context.userId}::uuid
        AND points > 0
    `.execute(database);
    const events = new Set(eventResult.rows.map((row) => row.event_type));

    const activityProgress = await sql<CountRow>`
      SELECT COUNT(*)::int AS total
      FROM (
        SELECT id FROM irrigation_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT id FROM treatment_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT id FROM fertilization_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT id FROM pruning_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT id FROM observation_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT id FROM expense_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
      ) activity
    `.execute(database);
    const activityTotal = activityProgress.rows[0]?.total ?? 0;

    const weekResult = await sql<WeekRow>`
      SELECT DISTINCT to_char(
        date_trunc('week', created_at AT TIME ZONE 'Europe/Madrid'),
        'YYYY-MM-DD'
      ) AS week_start
      FROM (
        SELECT created_at FROM irrigation_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT created_at FROM treatment_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT created_at FROM fertilization_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT created_at FROM pruning_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT created_at FROM observation_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
        UNION ALL
        SELECT created_at FROM expense_records WHERE workspace_id = ${context.workspaceId}::uuid AND created_by = ${context.userId}::uuid
      ) activity
      ORDER BY week_start DESC
      LIMIT 54
    `.execute(database);

    const currentWeekResult = await sql<CurrentWeekRow>`
      SELECT to_char(
        date_trunc('week', now() AT TIME ZONE 'Europe/Madrid'),
        'YYYY-MM-DD'
      ) AS current_week
    `.execute(database);
    const currentWeek = currentWeekResult.rows[0]?.current_week ?? new Date().toISOString().slice(0, 10);
    const rhythm = projectRhythm(weekResult.rows.map((row) => row.week_start), currentWeek);

    const dailyEarned = await getDailyEngagement(database, context.userId);
    const weeklyEarned = await getWeeklyOlives(database, context.userId);

    const recentResult = await sql<LedgerRow>`
      SELECT id::text, event_type, points, reason, created_at
      FROM mi_olivo_ledger
      WHERE user_id = ${context.userId}::uuid
      ORDER BY created_at DESC
      LIMIT 8
    `.execute(database);

    return {
      enabled,
      rule_version: RULE_VERSION,
      engagement_rule_version: ENGAGEMENT_RULE_VERSION,
      balance,
      level,
      level_label: levelLabel(level),
      tree_stage: Math.min(level, 5),
      progress: {
        current: progress,
        target: 100,
        percent: Math.min(100, progress),
      },
      rhythm,
      today: {
        earned: dailyEarned,
        cap: DAILY_ENGAGEMENT_CAP,
        remaining: Math.max(0, DAILY_ENGAGEMENT_CAP - dailyEarned),
      },
      weekly: {
        earned: weeklyEarned,
        goal: WEEKLY_OLIVE_GOAL,
        percent: Math.min(100, Math.round((weeklyEarned / WEEKLY_OLIVE_GOAL) * 100)),
      },
      earning_actions: [
        { id: 'field-work', title: 'Trabaja en tu finca', detail: 'Riego, poda, tratamiento, abonado, observaciones o gastos reales.', reward_label: '+4 · hasta 5/semana' },
        { id: 'territory', title: 'Explora Mágina', detail: 'Abre fichas de pueblos que todavía no conoces.', reward_label: '+3 por lugar' },
        { id: 'content', title: 'Mantente al día', detail: 'Lee noticias y eventos concretos, no simples recargas de página.', reward_label: '+2 por contenido' },
        { id: 'weather', title: 'Mira el clima', detail: 'Consulta el radar antes de organizar el trabajo de campo.', reward_label: '+2 al día' },
        { id: 'learning', title: 'Aprende algo útil', detail: 'Explora el cuaderno de consejos y pequeñas guías prácticas.', reward_label: '+5' },
      ],
      missions: [
        { id: 'profile', title: 'Prepara tu perfil', detail: 'Añade municipio y tu relación con el campo.', reward: 20, completed: events.has('profile_ready'), progress_current: events.has('profile_ready') ? 1 : 0, progress_target: 1 },
        { id: 'activity', title: 'Estrena tu cuaderno', detail: 'Registra una actividad real de una finca.', reward: 25, completed: events.has('first_activity'), progress_current: events.has('first_activity') ? 1 : 0, progress_target: 1 },
        { id: 'constancy', title: 'Organiza 10 actividades', detail: 'Suma diez registros reales de trabajo, riego, tratamiento, abonado, observación o gasto.', reward: 50, completed: events.has('ten_activities'), progress_current: Math.min(activityTotal, 10), progress_target: 10 },
        { id: 'document', title: 'Pon un documento en orden', detail: 'Guarda tu primer documento agrícola.', reward: 25, completed: events.has('first_document'), progress_current: events.has('first_document') ? 1 : 0, progress_target: 1 },
        { id: 'harvest', title: 'Registra una entrega', detail: 'Añade la primera entrega real de cosecha.', reward: 30, completed: events.has('first_harvest_delivery'), progress_current: events.has('first_harvest_delivery') ? 1 : 0, progress_target: 1 },
        { id: 'explore', title: 'Descubre Mágina', detail: 'Visita la ficha de un pueblo para conocer mejor el territorio.', reward: 3, completed: events.has('territory_viewed'), progress_current: events.has('territory_viewed') ? 1 : 0, progress_target: 1 },
        { id: 'weather', title: 'Mira antes de salir', detail: 'Consulta el radar antes de organizar el trabajo.', reward: 2, completed: events.has('weather_checked'), progress_current: events.has('weather_checked') ? 1 : 0, progress_target: 1 },
      ],
      achievements: [
        { id: 'roots', title: 'Primeras raíces', detail: 'Primera actividad registrada.', unlocked: events.has('first_activity') },
        { id: 'order', title: 'Cuaderno en orden', detail: 'Primer documento organizado.', unlocked: events.has('first_document') },
        { id: 'harvest', title: 'Primera cosecha', detail: 'Primera entrega registrada.', unlocked: events.has('first_harvest_delivery') },
        { id: 'constancy', title: 'Constancia', detail: 'Diez actividades reales organizadas.', unlocked: events.has('ten_activities') },
        { id: 'explorer', title: 'Raíces en Mágina', detail: 'Primera ficha de un pueblo explorada.', unlocked: events.has('territory_viewed') },
      ],
      rewards: [
        { id: 'sprout-badge', title: 'Distintivo Brote', detail: 'Tu primer progreso verificable en Mi Olivo.', required_level: 1, unlocked: balance > 0 },
        { id: 'new-branch-badge', title: 'Distintivo Rama nueva', detail: 'Tu olivo alcanza el nivel 2.', required_level: 2, unlocked: level >= 2 },
        { id: 'young-olive-badge', title: 'Distintivo Olivo joven', detail: 'Tu olivo alcanza el nivel 3.', required_level: 3, unlocked: level >= 3 },
        { id: 'rooted-olive-badge', title: 'Distintivo Olivo arraigado', detail: 'Tu olivo alcanza el nivel 4.', required_level: 4, unlocked: level >= 4 },
        { id: 'master-olive-badge', title: 'Distintivo Olivo maestro', detail: 'Tu olivo alcanza el nivel 5.', required_level: 5, unlocked: level >= 5 },
      ],
      recent: recentResult.rows,
    };
  });

  app.post('/api/v1/mi-olivo/events', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const input = parseBody(interactionSchema, request.body, reply);
    if (!input) return;
    if (!validInteractionSource(input.event_type, input.source_id)) {
      return reply.code(400).send({ error: 'Fuente de progreso no válida.' });
    }

    await ensureProfile(database, context.userId);
    const profile = await sql<ProfileState>`
      SELECT enabled
      FROM mi_olivo_profiles
      WHERE user_id = ${context.userId}::uuid
    `.execute(database);
    if (!(profile.rows[0]?.enabled ?? true)) {
      const earned = await getDailyEngagement(database, context.userId);
      return {
        awarded: false,
        points: 0,
        status: 'paused',
        message: 'Mi Olivo está pausado.',
        daily: { earned, cap: DAILY_ENGAGEMENT_CAP, remaining: Math.max(0, DAILY_ENGAGEMENT_CAP - earned) },
      };
    }

    const rule = interactionRules[input.event_type];
    const dailyEarned = await getDailyEngagement(database, context.userId);
    if (dailyEarned + rule.points > DAILY_ENGAGEMENT_CAP) {
      return {
        awarded: false,
        points: 0,
        status: 'daily_cap',
        message: 'Hoy ya has completado suficiente exploración útil. El campo también necesita descanso.',
        daily: { earned: dailyEarned, cap: DAILY_ENGAGEMENT_CAP, remaining: Math.max(0, DAILY_ENGAGEMENT_CAP - dailyEarned) },
      };
    }

    const clock = await getLocalClock(database);
    const inserted = await award(database, {
      userId: context.userId,
      workspaceId: context.workspaceId,
      eventType: input.event_type,
      sourceType: rule.sourceType,
      sourceId: input.source_id,
      points: rule.points,
      reason: rule.reason,
      ruleVersion: ENGAGEMENT_RULE_VERSION,
      idempotencyKey: engagementIdempotencyKey(input.event_type, input.source_id, context.workspaceId, clock.local_date),
    });
    const nextEarned = inserted ? dailyEarned + rule.points : dailyEarned;

    return {
      awarded: inserted,
      points: inserted ? rule.points : 0,
      status: inserted ? 'awarded' : 'already_recognized',
      message: inserted ? rule.reason : 'Esta acción ya estaba reconocida.',
      daily: {
        earned: nextEarned,
        cap: DAILY_ENGAGEMENT_CAP,
        remaining: Math.max(0, DAILY_ENGAGEMENT_CAP - nextEarned),
      },
    };
  });

  app.put('/api/v1/mi-olivo/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(preferenceSchema, request.body, reply);
    if (!input) return;

    const result = await sql<ProfileState>`
      INSERT INTO mi_olivo_profiles (user_id, enabled, updated_at)
      VALUES (${context.userId}::uuid, ${input.enabled}, now())
      ON CONFLICT (user_id)
      DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = now()
      RETURNING enabled
    `.execute(database);

    return { enabled: result.rows[0]?.enabled ?? input.enabled };
  });
}
