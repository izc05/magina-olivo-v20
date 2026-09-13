import type { FastifyInstance, FastifyRequest } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';
import { readAuthenticatedUserId } from '../request-context.js';

const routeIdParams = z.object({ id: z.string().uuid() });
const routeSlugParams = z.object({ slug: z.string().trim().min(1).max(180) });
const checkpointParams = z.object({ id: z.string().uuid(), checkpointId: z.string().uuid() });
const unlockSchema = z.object({
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  answer_key: z.string().trim().min(1).max(120).nullable().optional(),
});

function requireUser(request: FastifyRequest, reply: any) {
  const userId = readAuthenticatedUserId(request);
  if (!userId) {
    reply.code(401).send({ error: 'authentication_required' });
    return null;
  }
  return userId;
}

async function adventureRoute(db: DatabaseClient, routeId: string) {
  const result = await sql<{ id: string; name: string; enabled: boolean }>`
    SELECT r.id, r.name, COALESCE(a.enabled, false) AS enabled
    FROM routes r
    LEFT JOIN route_adventures a ON a.route_id = r.id
    WHERE r.id = ${routeId}::uuid
      AND r.status = 'published'
      AND r.track_status = 'validated'
    LIMIT 1
  `.execute(db);
  return result.rows[0] ?? null;
}

async function progressPayload(db: DatabaseClient, routeId: string, userId: string, runId?: string | null) {
  const statsResult = await sql<{
    total_checkpoints: number;
    required_checkpoints: number;
    total_points: number;
  }>`
    SELECT COUNT(*)::int AS total_checkpoints,
           COUNT(*) FILTER (WHERE is_required)::int AS required_checkpoints,
           COALESCE(SUM(points), 0)::int AS total_points
    FROM route_adventure_checkpoints
    WHERE route_id = ${routeId}::uuid AND active = true
  `.execute(db);
  const baseStats = statsResult.rows[0] ?? { total_checkpoints: 0, required_checkpoints: 0, total_points: 0 };

  if (!runId) {
    return {
      run: null,
      stats: {
        ...baseStats,
        unlocked_checkpoints: 0,
        required_unlocked: 0,
        required_remaining: baseStats.required_checkpoints,
      },
      unlocks: [],
      badges: [],
    };
  }

  const [runResult, unlockResult, unlockedStatsResult] = await Promise.all([
    sql<Record<string, unknown>>`
      SELECT id, route_id, status, score, started_at, completed_at, updated_at, last_distance_m
      FROM route_adventure_runs
      WHERE id = ${runId}::uuid AND route_id = ${routeId}::uuid AND user_id = ${userId}::uuid
      LIMIT 1
    `.execute(db),
    sql<Record<string, unknown>>`
      SELECT u.checkpoint_id, u.unlocked_at, u.answer_key, u.is_correct, u.awarded_points,
             u.distance_to_checkpoint_m, cp.title, cp.kind, cp.is_required
      FROM route_adventure_unlocks u
      JOIN route_adventure_checkpoints cp ON cp.id = u.checkpoint_id
      WHERE u.run_id = ${runId}::uuid
      ORDER BY u.unlocked_at, cp.sort_order, cp.distance_m NULLS LAST
    `.execute(db),
    sql<{ unlocked_checkpoints: number; required_unlocked: number }>`
      SELECT COUNT(*)::int AS unlocked_checkpoints,
             COUNT(*) FILTER (WHERE cp.is_required)::int AS required_unlocked
      FROM route_adventure_unlocks u
      JOIN route_adventure_checkpoints cp ON cp.id = u.checkpoint_id
      WHERE u.run_id = ${runId}::uuid AND cp.active = true
    `.execute(db),
  ]);

  const unlockedStats = unlockedStatsResult.rows[0] ?? { unlocked_checkpoints: 0, required_unlocked: 0 };
  const requiredRemaining = Math.max(0, baseStats.required_checkpoints - unlockedStats.required_unlocked);
  const badges: string[] = [];
  if (unlockedStats.unlocked_checkpoints > 0) badges.push('primer_paso');
  if (requiredRemaining === 0 && baseStats.required_checkpoints > 0) badges.push('explorador_magina');
  if (baseStats.total_checkpoints > 0 && unlockedStats.unlocked_checkpoints >= baseStats.total_checkpoints) badges.push('ruta_100');

  return {
    run: runResult.rows[0] ?? null,
    stats: {
      ...baseStats,
      ...unlockedStats,
      required_remaining: requiredRemaining,
    },
    unlocks: unlockResult.rows,
    badges,
  };
}

export function registerRouteAdventureRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/public/routes/:slug/adventure', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const params = routeSlugParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_slug' });

    const routeResult = await sql<Record<string, unknown>>`
      SELECT r.id, r.slug, r.name, COALESCE(a.enabled, false) AS enabled,
             a.title, a.intro, a.completion_message
      FROM routes r
      LEFT JOIN route_adventures a ON a.route_id = r.id
      WHERE r.slug = ${params.data.slug}
        AND r.status = 'published'
        AND r.track_status = 'validated'
      LIMIT 1
    `.execute(db);
    const route = routeResult.rows[0];
    if (!route) return reply.code(404).send({ error: 'route_not_found' });
    if (!route.enabled) return { enabled: false, route: { id: route.id, slug: route.slug, name: route.name }, adventure: null, checkpoints: [] };

    const checkpoints = await sql<Record<string, unknown>>`
      SELECT cp.id, cp.route_point_id, cp.title, cp.description, cp.kind, cp.distance_m,
             cp.unlock_radius_m, cp.points, cp.is_required, cp.question, cp.answer_options,
             cp.hint, cp.sort_order,
             ST_Y(cp.location) AS latitude, ST_X(cp.location) AS longitude
      FROM route_adventure_checkpoints cp
      WHERE cp.route_id = ${String(route.id)}::uuid AND cp.active = true
      ORDER BY cp.sort_order, cp.distance_m NULLS LAST, cp.created_at
    `.execute(db);

    return {
      enabled: true,
      route: { id: route.id, slug: route.slug, name: route.name },
      adventure: {
        title: route.title,
        intro: route.intro,
        completion_message: route.completion_message,
      },
      checkpoints: checkpoints.rows,
      notice: 'El Modo Aventura es una capa lúdica. No sustituye el track, la señalización, los avisos oficiales ni las recomendaciones de seguridad.',
    };
  });

  app.get('/api/v1/routes/:id/adventure/progress', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const route = await adventureRoute(db, params.data.id);
    if (!route || !route.enabled) return reply.code(404).send({ error: 'adventure_not_available' });

    const runResult = await sql<{ id: string }>`
      SELECT id FROM route_adventure_runs
      WHERE route_id = ${params.data.id}::uuid AND user_id = ${userId}::uuid
      ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'completed' THEN 1 ELSE 2 END, started_at DESC
      LIMIT 1
    `.execute(db);
    return progressPayload(db, params.data.id, userId, runResult.rows[0]?.id ?? null);
  });

  app.post('/api/v1/routes/:id/adventure/start', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    const route = await adventureRoute(db, params.data.id);
    if (!route || !route.enabled) return reply.code(404).send({ error: 'adventure_not_available' });

    const checkpoints = await sql<{ total: number }>`
      SELECT COUNT(*)::int AS total
      FROM route_adventure_checkpoints
      WHERE route_id = ${params.data.id}::uuid AND active = true
    `.execute(db);
    if ((checkpoints.rows[0]?.total ?? 0) === 0) return reply.code(409).send({ error: 'adventure_has_no_checkpoints' });

    let run = await sql<{ id: string }>`
      SELECT id FROM route_adventure_runs
      WHERE route_id = ${params.data.id}::uuid AND user_id = ${userId}::uuid AND status = 'active'
      LIMIT 1
    `.execute(db);
    if (!run.rows[0]) {
      run = await sql<{ id: string }>`
        INSERT INTO route_adventure_runs(route_id, user_id)
        VALUES (${params.data.id}::uuid, ${userId}::uuid)
        RETURNING id
      `.execute(db);
    }
    return reply.code(201).send(await progressPayload(db, params.data.id, userId, run.rows[0].id));
  });

  app.post('/api/v1/routes/:id/adventure/checkpoints/:checkpointId/unlock', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = checkpointParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_adventure_checkpoint' });
    const input = parseBody(unlockSchema, request.body, reply); if (!input) return;

    const runResult = await sql<{ id: string }>`
      SELECT ar.id
      FROM route_adventure_runs ar
      JOIN route_adventures a ON a.route_id = ar.route_id AND a.enabled = true
      JOIN routes r ON r.id = ar.route_id AND r.status = 'published' AND r.track_status = 'validated'
      WHERE ar.route_id = ${params.data.id}::uuid AND ar.user_id = ${userId}::uuid AND ar.status = 'active'
      LIMIT 1
    `.execute(db);
    const run = runResult.rows[0];
    if (!run) return reply.code(409).send({ error: 'adventure_not_started' });

    const checkpointResult = await sql<{
      id: string;
      title: string;
      question: string | null;
      correct_answer_key: string | null;
      points: number;
      distance_m: number | null;
      unlock_radius_m: number;
      distance_to_checkpoint_m: number;
    }>`
      SELECT cp.id, cp.title, cp.question, cp.correct_answer_key, cp.points, cp.distance_m,
             cp.unlock_radius_m,
             ST_DistanceSphere(
               cp.location,
               ST_SetSRID(ST_MakePoint(${input.longitude}, ${input.latitude}), 4326)
             ) AS distance_to_checkpoint_m
      FROM route_adventure_checkpoints cp
      WHERE cp.id = ${params.data.checkpointId}::uuid
        AND cp.route_id = ${params.data.id}::uuid
        AND cp.active = true
      LIMIT 1
    `.execute(db);
    const checkpoint = checkpointResult.rows[0];
    if (!checkpoint) return reply.code(404).send({ error: 'checkpoint_not_found' });

    const distance = Number(checkpoint.distance_to_checkpoint_m);
    if (!Number.isFinite(distance) || distance > checkpoint.unlock_radius_m) {
      return reply.code(409).send({
        error: 'checkpoint_too_far',
        distance_m: Number.isFinite(distance) ? Math.round(distance) : null,
        unlock_radius_m: checkpoint.unlock_radius_m,
      });
    }

    if (checkpoint.question) {
      if (!input.answer_key) return reply.code(400).send({ error: 'checkpoint_answer_required' });
      if (input.answer_key !== checkpoint.correct_answer_key) {
        return { unlocked: false, correct: false, checkpoint_id: checkpoint.id, distance_m: Math.round(distance) };
      }
    }

    // The supplied GPS coordinate is used only inside the PostGIS proximity calculation
    // above. We intentionally do not persist exact user coordinates.
    await sql`
      INSERT INTO route_adventure_unlocks(
        run_id, checkpoint_id, answer_key, is_correct, awarded_points, distance_to_checkpoint_m
      ) VALUES (
        ${run.id}::uuid, ${checkpoint.id}::uuid, ${input.answer_key ?? null},
        ${checkpoint.question ? true : null}, ${checkpoint.points}, ${distance}
      )
      ON CONFLICT (run_id, checkpoint_id) DO NOTHING
    `.execute(db);

    await sql`
      UPDATE route_adventure_runs
      SET score = (
            SELECT COALESCE(SUM(awarded_points), 0)::int
            FROM route_adventure_unlocks
            WHERE run_id = ${run.id}::uuid
          ),
          last_distance_m = COALESCE(${checkpoint.distance_m}, last_distance_m),
          updated_at = now()
      WHERE id = ${run.id}::uuid
    `.execute(db);

    return { unlocked: true, correct: checkpoint.question ? true : null, progress: await progressPayload(db, params.data.id, userId, run.id) };
  });

  app.post('/api/v1/routes/:id/adventure/complete', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });

    const runResult = await sql<{ id: string }>`
      SELECT id FROM route_adventure_runs
      WHERE route_id = ${params.data.id}::uuid AND user_id = ${userId}::uuid AND status = 'active'
      LIMIT 1
    `.execute(db);
    const run = runResult.rows[0];
    if (!run) return reply.code(409).send({ error: 'adventure_not_started' });

    const progress = await progressPayload(db, params.data.id, userId, run.id);
    const stats = progress.stats;
    if (stats.total_checkpoints === 0 || stats.unlocked_checkpoints === 0 || stats.required_remaining > 0) {
      return reply.code(409).send({
        error: 'adventure_incomplete',
        required_remaining: stats.required_remaining,
        unlocked_checkpoints: stats.unlocked_checkpoints,
        total_checkpoints: stats.total_checkpoints,
      });
    }

    await sql`
      UPDATE route_adventure_runs
      SET status = 'completed', completed_at = now(), updated_at = now()
      WHERE id = ${run.id}::uuid
    `.execute(db);
    return { completed: true, progress: await progressPayload(db, params.data.id, userId, run.id) };
  });

  app.post('/api/v1/routes/:id/adventure/abandon', async (request, reply) => {
    if (!db) return reply.code(503).send({ error: 'database_not_configured' });
    const userId = requireUser(request, reply); if (!userId) return;
    const params = routeIdParams.safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_route_id' });
    await sql`
      UPDATE route_adventure_runs
      SET status = 'abandoned', updated_at = now()
      WHERE route_id = ${params.data.id}::uuid AND user_id = ${userId}::uuid AND status = 'active'
    `.execute(db);
    return reply.code(204).send();
  });
}
