import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import { parseBody } from '../http/helpers.js';

const routeParams = z.object({ id: z.string().uuid() });
const checkpointParams = z.object({ id: z.string().uuid(), checkpointId: z.string().uuid() });

const adventureSchema = z.object({
  enabled: z.boolean().default(false),
  title: z.string().trim().min(1).max(160).default('Modo Aventura'),
  intro: z.string().trim().max(4000).nullable().optional(),
  completion_message: z.string().trim().max(4000).nullable().optional(),
});

const answerOptionSchema = z.object({
  key: z.string().trim().min(1).max(120),
  label: z.string().trim().min(1).max(500),
});

const checkpointSchema = z.object({
  route_point_id: z.string().uuid().nullable().optional(),
  title: z.string().trim().min(1).max(180),
  description: z.string().trim().max(4000).nullable().optional(),
  kind: z.enum(['landmark','trivia','observation','photo','collection','rest']).default('landmark'),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  distance_m: z.number().min(0).nullable().optional(),
  unlock_radius_m: z.number().int().min(10).max(500).default(60),
  points: z.number().int().min(0).max(10000).default(100),
  is_required: z.boolean().default(true),
  question: z.string().trim().max(1000).nullable().optional(),
  answer_options: z.array(answerOptionSchema).max(12).default([]),
  correct_answer_key: z.string().trim().min(1).max(120).nullable().optional(),
  hint: z.string().trim().max(1000).nullable().optional(),
  sort_order: z.number().int().min(-10000).max(10000).default(0),
  active: z.boolean().default(true),
}).superRefine((value, ctx) => {
  const hasQuestion = Boolean(value.question?.trim());
  const hasAnswer = Boolean(value.correct_answer_key?.trim());
  if (hasQuestion !== hasAnswer) ctx.addIssue({ code: 'custom', message: 'question_and_correct_answer_required_together' });
  if (hasQuestion && value.answer_options.length < 2) ctx.addIssue({ code: 'custom', message: 'question_requires_at_least_two_options' });
  if (hasQuestion && !value.answer_options.some((option) => option.key === value.correct_answer_key)) {
    ctx.addIssue({ code: 'custom', message: 'correct_answer_must_match_option' });
  }
  if (new Set(value.answer_options.map((option) => option.key)).size !== value.answer_options.length) {
    ctx.addIssue({ code: 'custom', message: 'answer_option_keys_must_be_unique' });
  }
});

async function routeExists(database: DatabaseClient, id: string) {
  const result = await sql<{ id: string }>`SELECT id FROM routes WHERE id = ${id}::uuid LIMIT 1`.execute(database);
  return Boolean(result.rows[0]);
}

async function validateRoutePoint(database: DatabaseClient, routeId: string, routePointId: string | null | undefined) {
  if (!routePointId) return true;
  const result = await sql<{ id: string }>`
    SELECT id FROM route_points WHERE id = ${routePointId}::uuid AND route_id = ${routeId}::uuid LIMIT 1
  `.execute(database);
  return Boolean(result.rows[0]);
}

export function registerAdminRouteAdventureRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/routes/:id/adventure', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'support');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    if (!await routeExists(auth.database, params.data.id)) return reply.code(404).send({ error: 'route_not_found' });

    const [configResult, checkpointsResult, pointsResult, metricsResult] = await Promise.all([
      sql<Record<string, unknown>>`
        SELECT route_id, enabled, title, intro, completion_message, created_at, updated_at
        FROM route_adventures WHERE route_id = ${params.data.id}::uuid LIMIT 1
      `.execute(auth.database),
      sql<Record<string, unknown>>`
        SELECT cp.id, cp.route_point_id, cp.title, cp.description, cp.kind, cp.distance_m,
               cp.unlock_radius_m, cp.points, cp.is_required, cp.question, cp.answer_options,
               cp.correct_answer_key, cp.hint, cp.sort_order, cp.active,
               ST_Y(cp.location) AS latitude, ST_X(cp.location) AS longitude
        FROM route_adventure_checkpoints cp
        WHERE cp.route_id = ${params.data.id}::uuid
        ORDER BY cp.sort_order, cp.distance_m NULLS LAST, cp.created_at
      `.execute(auth.database),
      sql<Record<string, unknown>>`
        SELECT id, name, kind, distance_m, ST_Y(location) AS latitude, ST_X(location) AS longitude
        FROM route_points
        WHERE route_id = ${params.data.id}::uuid AND active = true
        ORDER BY sort_order, distance_m NULLS LAST, name
      `.execute(auth.database),
      sql<Record<string, unknown>>`
        SELECT
          COUNT(*) FILTER (WHERE status = 'active')::int AS active_runs,
          COUNT(*) FILTER (WHERE status = 'completed')::int AS completed_runs,
          COUNT(*) FILTER (WHERE status = 'abandoned')::int AS abandoned_runs,
          COUNT(*)::int AS total_runs,
          COALESCE(ROUND(AVG(score)::numeric, 1), 0) AS average_score
        FROM route_adventure_runs
        WHERE route_id = ${params.data.id}::uuid
      `.execute(auth.database),
    ]);

    return {
      adventure: configResult.rows[0] ?? {
        route_id: params.data.id,
        enabled: false,
        title: 'Modo Aventura',
        intro: null,
        completion_message: null,
      },
      checkpoints: checkpointsResult.rows,
      route_points: pointsResult.rows,
      metrics: metricsResult.rows[0] ?? { active_runs: 0, completed_runs: 0, abandoned_runs: 0, total_runs: 0, average_score: 0 },
    };
  });

  app.put('/api/v1/admin/routes/:id/adventure', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(adventureSchema, request.body, reply); if (!input) return;
    if (!await routeExists(auth.database, params.data.id)) return reply.code(404).send({ error: 'route_not_found' });

    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_adventures(route_id, enabled, title, intro, completion_message, created_by, updated_by)
      VALUES (
        ${params.data.id}::uuid, ${input.enabled}, ${input.title}, ${input.intro ?? null},
        ${input.completion_message ?? null}, ${auth.access.userId}::uuid, ${auth.access.userId}::uuid
      )
      ON CONFLICT (route_id) DO UPDATE SET
        enabled = EXCLUDED.enabled,
        title = EXCLUDED.title,
        intro = EXCLUDED.intro,
        completion_message = EXCLUDED.completion_message,
        updated_by = EXCLUDED.updated_by,
        updated_at = now()
      RETURNING route_id, enabled, title, intro, completion_message, created_at, updated_at
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'route.adventure_updated', 'route', params.data.id, { enabled: input.enabled });
    return { adventure: result.rows[0] };
  });

  app.post('/api/v1/admin/routes/:id/adventure/checkpoints', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = routeParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const input = parseBody(checkpointSchema, request.body, reply); if (!input) return;
    if (!await routeExists(auth.database, params.data.id)) return reply.code(404).send({ error: 'route_not_found' });
    if (!await validateRoutePoint(auth.database, params.data.id, input.route_point_id)) return reply.code(400).send({ error: 'route_point_not_in_route' });

    const result = await sql<Record<string, unknown>>`
      INSERT INTO route_adventure_checkpoints(
        route_id, route_point_id, title, description, kind, location, distance_m,
        unlock_radius_m, points, is_required, question, answer_options, correct_answer_key,
        hint, sort_order, active
      ) VALUES (
        ${params.data.id}::uuid, ${input.route_point_id ?? null}::uuid, ${input.title}, ${input.description ?? null},
        ${input.kind}, ST_SetSRID(ST_MakePoint(${input.longitude}, ${input.latitude}), 4326), ${input.distance_m ?? null},
        ${input.unlock_radius_m}, ${input.points}, ${input.is_required}, ${input.question ?? null},
        ${JSON.stringify(input.answer_options)}::jsonb, ${input.correct_answer_key ?? null}, ${input.hint ?? null},
        ${input.sort_order}, ${input.active}
      )
      RETURNING id, route_id, route_point_id, title, description, kind, distance_m, unlock_radius_m,
                points, is_required, question, answer_options, correct_answer_key, hint, sort_order, active,
                ST_Y(location) AS latitude, ST_X(location) AS longitude
    `.execute(auth.database);
    const checkpoint = result.rows[0];
    await auditAdminAction(auth.database, auth.access, 'route.adventure_checkpoint_created', 'route_adventure_checkpoint', String(checkpoint.id), { route_id: params.data.id, kind: input.kind });
    return reply.code(201).send({ checkpoint });
  });

  app.patch('/api/v1/admin/routes/:id/adventure/checkpoints/:checkpointId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = checkpointParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_adventure_checkpoint' });
    const input = parseBody(checkpointSchema, request.body, reply); if (!input) return;
    if (!await validateRoutePoint(auth.database, params.data.id, input.route_point_id)) return reply.code(400).send({ error: 'route_point_not_in_route' });

    const result = await sql<Record<string, unknown>>`
      UPDATE route_adventure_checkpoints SET
        route_point_id = ${input.route_point_id ?? null}::uuid,
        title = ${input.title}, description = ${input.description ?? null}, kind = ${input.kind},
        location = ST_SetSRID(ST_MakePoint(${input.longitude}, ${input.latitude}), 4326),
        distance_m = ${input.distance_m ?? null}, unlock_radius_m = ${input.unlock_radius_m},
        points = ${input.points}, is_required = ${input.is_required}, question = ${input.question ?? null},
        answer_options = ${JSON.stringify(input.answer_options)}::jsonb,
        correct_answer_key = ${input.correct_answer_key ?? null}, hint = ${input.hint ?? null},
        sort_order = ${input.sort_order}, active = ${input.active}, updated_at = now()
      WHERE id = ${params.data.checkpointId}::uuid AND route_id = ${params.data.id}::uuid
      RETURNING id, route_id, route_point_id, title, description, kind, distance_m, unlock_radius_m,
                points, is_required, question, answer_options, correct_answer_key, hint, sort_order, active,
                ST_Y(location) AS latitude, ST_X(location) AS longitude
    `.execute(auth.database);
    const checkpoint = result.rows[0];
    if (!checkpoint) return reply.code(404).send({ error: 'checkpoint_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.adventure_checkpoint_updated', 'route_adventure_checkpoint', params.data.checkpointId, { route_id: params.data.id, kind: input.kind });
    return { checkpoint };
  });

  app.delete('/api/v1/admin/routes/:id/adventure/checkpoints/:checkpointId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'editor');
    if (!auth) return;
    const params = checkpointParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_adventure_checkpoint' });
    const result = await sql<{ id: string }>`
      DELETE FROM route_adventure_checkpoints
      WHERE id = ${params.data.checkpointId}::uuid AND route_id = ${params.data.id}::uuid
      RETURNING id
    `.execute(auth.database);
    if (!result.rows[0]) return reply.code(404).send({ error: 'checkpoint_not_found' });
    await auditAdminAction(auth.database, auth.access, 'route.adventure_checkpoint_deleted', 'route_adventure_checkpoint', params.data.checkpointId, { route_id: params.data.id });
    return reply.code(204).send();
  });
}
