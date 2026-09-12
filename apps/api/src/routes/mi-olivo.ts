import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

const RULE_VERSION = 'mi-olivo-v1';
const preferenceSchema = z.object({ enabled: z.boolean() });

type ProfileState = { enabled: boolean };
type BooleanRow = { complete: boolean };
type SourceRow = { source_id: string };
type CountRow = { total: number };
type BalanceRow = { balance: number };
type EventTypeRow = { event_type: string };
type WeekRow = { week_start: string };
type CurrentWeekRow = { current_week: string };
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
};

async function award(database: DatabaseClient, input: AwardInput) {
  const idempotencyKey = `${RULE_VERSION}:${input.eventType}`;
  await sql`
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
      ${RULE_VERSION},
      ${idempotencyKey}
    )
    ON CONFLICT (user_id, idempotency_key) DO NOTHING
  `.execute(database);
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

    await sql`
      INSERT INTO mi_olivo_profiles (user_id)
      VALUES (${context.userId}::uuid)
      ON CONFLICT (user_id) DO NOTHING
    `.execute(database);

    const profile = await sql<ProfileState>`
      SELECT enabled
      FROM mi_olivo_profiles
      WHERE user_id = ${context.userId}::uuid
    `.execute(database);
    const enabled = profile.rows[0]?.enabled ?? true;

    if (enabled) {
      await reconcileVerifiedEvents(database, context.userId, context.workspaceId);
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
      missions: [
        { id: 'profile', title: 'Prepara tu perfil', detail: 'Añade municipio y tu relación con el campo.', reward: 20, completed: events.has('profile_ready'), progress_current: events.has('profile_ready') ? 1 : 0, progress_target: 1 },
        { id: 'activity', title: 'Estrena tu cuaderno', detail: 'Registra una actividad real de una finca.', reward: 25, completed: events.has('first_activity'), progress_current: events.has('first_activity') ? 1 : 0, progress_target: 1 },
        { id: 'constancy', title: 'Organiza 10 actividades', detail: 'Suma diez registros reales de trabajo, riego, tratamiento, abonado, observación o gasto.', reward: 50, completed: events.has('ten_activities'), progress_current: Math.min(activityTotal, 10), progress_target: 10 },
        { id: 'document', title: 'Pon un documento en orden', detail: 'Guarda tu primer documento agrícola.', reward: 25, completed: events.has('first_document'), progress_current: events.has('first_document') ? 1 : 0, progress_target: 1 },
        { id: 'harvest', title: 'Registra una entrega', detail: 'Añade la primera entrega real de cosecha.', reward: 30, completed: events.has('first_harvest_delivery'), progress_current: events.has('first_harvest_delivery') ? 1 : 0, progress_target: 1 },
      ],
      achievements: [
        { id: 'roots', title: 'Primeras raíces', detail: 'Primera actividad registrada.', unlocked: events.has('first_activity') },
        { id: 'order', title: 'Cuaderno en orden', detail: 'Primer documento organizado.', unlocked: events.has('first_document') },
        { id: 'harvest', title: 'Primera cosecha', detail: 'Primera entrega registrada.', unlocked: events.has('first_harvest_delivery') },
        { id: 'constancy', title: 'Constancia', detail: 'Diez actividades reales organizadas.', unlocked: events.has('ten_activities') },
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
