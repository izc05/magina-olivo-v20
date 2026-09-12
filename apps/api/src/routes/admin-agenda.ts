import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';
import { parseBody } from '../http/helpers.js';

const datePattern = /^\d{4}-\d{2}-\d{2}$/;
const taskKindSchema = z.enum(['treatment','irrigation','fertilization','pruning','harvest','work','observation','other']);
const taskStatusSchema = z.enum(['planned','postponed','completed','cancelled']);

const listSchema = z.object({
  q: z.string().trim().max(120).optional(),
  workspace_id: z.string().uuid().optional(),
  field_id: z.string().uuid().optional(),
  status: z.enum(['all','active','planned','postponed','completed','cancelled']).default('active'),
  from: z.string().regex(datePattern).optional(),
  to: z.string().regex(datePattern).optional(),
  limit: z.coerce.number().int().min(1).max(500).default(200),
}).refine((value) => !value.from || !value.to || value.from <= value.to, { path: ['to'], message: 'invalid_range' });

const createSchema = z.object({
  field_id: z.string().uuid(),
  title: z.string().trim().min(1).max(180),
  scheduled_at: z.string().datetime({ offset: true }),
  task_kind: taskKindSchema,
  notes: z.string().trim().max(3000).nullable().optional(),
});

const updateSchema = z.object({
  title: z.string().trim().min(1).max(180).optional(),
  scheduled_at: z.string().datetime({ offset: true }).optional(),
  task_kind: taskKindSchema.optional(),
  notes: z.string().trim().max(3000).nullable().optional(),
  status: z.enum(['planned','postponed','cancelled']).optional(),
}).refine((value) => Object.keys(value).length > 0, 'empty_update');

type AdminTaskRow = {
  id: string;
  workspace_id: string;
  workspace_name: string;
  field_id: string | null;
  field_name: string | null;
  title: string;
  scheduled_at: Date | string;
  status: 'planned' | 'postponed' | 'completed' | 'cancelled';
  source: string;
  source_domain_type: string | null;
  source_domain_record_id: string | null;
  task_kind: string | null;
  notes: string | null;
  created_by: string | null;
  created_by_name: string | null;
  completed_domain_type: string | null;
  completed_domain_record_id: string | null;
  completed_at: Date | string | null;
  created_at: Date | string;
  updated_at: Date | string;
};

export function registerAdminAgendaRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/agenda', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const parsed = listSchema.safeParse(request.query);
    if (!parsed.success) return reply.code(400).send({ error: 'validation_error', issues: parsed.error.issues });
    const input = parsed.data;
    const term = input.q ? `%${input.q}%` : null;

    const result = await sql<AdminTaskRow>`
      SELECT se.id::text, se.workspace_id::text, w.name AS workspace_name,
             se.field_id::text, f.name AS field_name, se.title, se.scheduled_at, se.status,
             se.source, se.source_domain_type, se.source_domain_record_id::text, se.task_kind, se.notes,
             se.created_by::text, u.display_name AS created_by_name,
             se.completed_domain_type, se.completed_domain_record_id::text, se.completed_at,
             se.created_at, se.updated_at
      FROM scheduled_events se
      JOIN workspaces w ON w.id = se.workspace_id
      LEFT JOIN fields f ON f.id = se.field_id AND f.workspace_id = se.workspace_id
      LEFT JOIN users u ON u.id = se.created_by
      WHERE (${input.workspace_id ?? null}::uuid IS NULL OR se.workspace_id = ${input.workspace_id ?? null}::uuid)
        AND (${input.field_id ?? null}::uuid IS NULL OR se.field_id = ${input.field_id ?? null}::uuid)
        AND (
          ${input.status} = 'all'
          OR (${input.status} = 'active' AND se.status IN ('planned','postponed'))
          OR se.status = ${input.status}
        )
        AND (${input.from ?? null}::date IS NULL OR se.scheduled_at >= ${input.from ?? null}::date)
        AND (${input.to ?? null}::date IS NULL OR se.scheduled_at < (${input.to ?? null}::date + interval '1 day'))
        AND (${term}::text IS NULL OR se.title ILIKE ${term} OR w.name ILIKE ${term} OR f.name ILIKE ${term})
      ORDER BY
        CASE se.status WHEN 'planned' THEN 0 WHEN 'postponed' THEN 1 WHEN 'completed' THEN 2 ELSE 3 END,
        se.scheduled_at ASC,
        se.id ASC
      LIMIT ${input.limit}
    `.execute(auth.database);

    const counts = await sql<{ planned: number; postponed: number; overdue: number; completed_30d: number }>`
      SELECT
        count(*) FILTER (WHERE status='planned')::int AS planned,
        count(*) FILTER (WHERE status='postponed')::int AS postponed,
        count(*) FILTER (WHERE status IN ('planned','postponed') AND scheduled_at < now())::int AS overdue,
        count(*) FILTER (WHERE status='completed' AND completed_at >= now() - interval '30 days')::int AS completed_30d
      FROM scheduled_events
    `.execute(auth.database);

    return { tasks: result.rows, counts: counts.rows[0] ?? { planned: 0, postponed: 0, overdue: 0, completed_30d: 0 } };
  });

  app.post('/api/v1/admin/agenda', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const input = parseBody(createSchema, request.body, reply);
    if (!input) return;

    const field = await auth.database.selectFrom('fields')
      .select(['id', 'workspace_id', 'name', 'status'])
      .where('id', '=', input.field_id)
      .executeTakeFirst();
    if (!field || field.status !== 'active') return reply.code(404).send({ error: 'field_not_found_or_archived' });

    const id = randomUUID();
    const result = await sql<AdminTaskRow>`
      INSERT INTO scheduled_events (
        id, workspace_id, field_id, source_domain_type, title, scheduled_at, status, source,
        task_kind, notes, created_by
      ) VALUES (
        ${id}::uuid, ${field.workspace_id}::uuid, ${field.id}::uuid, ${input.task_kind}, ${input.title},
        ${input.scheduled_at}::timestamptz, 'planned', 'manual', ${input.task_kind}, ${input.notes ?? null}, ${auth.access.userId}::uuid
      )
      RETURNING id::text, workspace_id::text,
        (SELECT name FROM workspaces WHERE id=workspace_id) AS workspace_name,
        field_id::text, ${field.name}::text AS field_name, title, scheduled_at, status, source,
        source_domain_type, source_domain_record_id::text, task_kind, notes, created_by::text,
        ${auth.access.email}::text AS created_by_name, completed_domain_type,
        completed_domain_record_id::text, completed_at, created_at, updated_at
    `.execute(auth.database);
    await auditAdminAction(auth.database, auth.access, 'scheduled_event.created', 'scheduled_event', id, {
      workspace_id: field.workspace_id,
      field_id: field.id,
      task_kind: input.task_kind,
      scheduled_at: input.scheduled_at,
    });
    return reply.code(201).send({ task: result.rows[0] });
  });

  app.patch('/api/v1/admin/agenda/:taskId', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const params = z.object({ taskId: z.string().uuid() }).safeParse(request.params);
    if (!params.success) return reply.code(400).send({ error: 'invalid_task_id' });
    const input = parseBody(updateSchema, request.body, reply);
    if (!input) return;

    const existing = await sql<AdminTaskRow>`
      SELECT se.id::text, se.workspace_id::text, w.name AS workspace_name,
             se.field_id::text, f.name AS field_name, se.title, se.scheduled_at, se.status,
             se.source, se.source_domain_type, se.source_domain_record_id::text, se.task_kind, se.notes,
             se.created_by::text, u.display_name AS created_by_name,
             se.completed_domain_type, se.completed_domain_record_id::text, se.completed_at,
             se.created_at, se.updated_at
      FROM scheduled_events se
      JOIN workspaces w ON w.id=se.workspace_id
      LEFT JOIN fields f ON f.id=se.field_id
      LEFT JOIN users u ON u.id=se.created_by
      WHERE se.id=${params.data.taskId}::uuid
      LIMIT 1
    `.execute(auth.database);
    const current = existing.rows[0];
    if (!current) return reply.code(404).send({ error: 'scheduled_event_not_found' });
    if (current.status === 'completed') return reply.code(409).send({ error: 'completed_task_is_immutable' });

    const notesSupplied = Object.prototype.hasOwnProperty.call(input, 'notes');
    const result = await sql<AdminTaskRow>`
      UPDATE scheduled_events se
      SET title = COALESCE(${input.title ?? null}, se.title),
          scheduled_at = COALESCE(${input.scheduled_at ?? null}::timestamptz, se.scheduled_at),
          task_kind = COALESCE(${input.task_kind ?? null}, se.task_kind),
          notes = CASE WHEN ${notesSupplied} THEN ${input.notes ?? null} ELSE se.notes END,
          status = COALESCE(${input.status ?? null}, se.status),
          updated_at = now()
      FROM workspaces w
      LEFT JOIN fields f ON f.id=se.field_id AND f.workspace_id=se.workspace_id
      LEFT JOIN users u ON u.id=se.created_by
      WHERE se.id=${current.id}::uuid AND w.id=se.workspace_id
      RETURNING se.id::text, se.workspace_id::text, w.name AS workspace_name,
                se.field_id::text, f.name AS field_name, se.title, se.scheduled_at, se.status,
                se.source, se.source_domain_type, se.source_domain_record_id::text, se.task_kind, se.notes,
                se.created_by::text, u.display_name AS created_by_name,
                se.completed_domain_type, se.completed_domain_record_id::text, se.completed_at,
                se.created_at, se.updated_at
    `.execute(auth.database);
    const task = result.rows[0]!;
    await auditAdminAction(auth.database, auth.access, 'scheduled_event.updated', 'scheduled_event', current.id, {
      workspace_id: current.workspace_id,
      from: { title: current.title, scheduled_at: current.scheduled_at, task_kind: current.task_kind, notes: current.notes, status: current.status },
      to: { title: task.title, scheduled_at: task.scheduled_at, task_kind: task.task_kind, notes: task.notes, status: task.status },
    });
    return { task };
  });
}
