import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { fieldBelongsToWorkspace, requireContext, requireDatabase } from '../http/helpers.js';

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const taskKindSchema = z.enum(['treatment','irrigation','fertilization','pruning','harvest','work','observation','other']);

const createSchema = z.object({
  title: z.string().trim().min(1).max(180),
  scheduled_at: z.string().datetime({ offset: true }),
  task_kind: taskKindSchema,
  notes: z.string().trim().max(3000).optional().nullable(),
});

const updateSchema = z.object({
  scheduled_at: z.string().datetime({ offset: true }).optional(),
  title: z.string().trim().min(1).max(180).optional(),
  notes: z.string().trim().max(3000).optional().nullable(),
  status: z.enum(['planned','postponed','cancelled']).optional(),
}).refine((value) => Object.keys(value).length > 0, 'No changes supplied');

const completeSchema = z.object({
  domain_type: z.enum(['treatment','irrigation','fertilization','pruning','observation','harvest_delivery','work']),
  domain_record_id: z.string().uuid(),
});

type TaskRow = {
  id: string;
  field_id: string;
  field_name: string;
  title: string;
  scheduled_at: Date | string;
  status: 'planned' | 'completed' | 'postponed' | 'cancelled';
  task_kind: string | null;
  notes: string | null;
  completed_domain_type: string | null;
  completed_domain_record_id: string | null;
  completed_at: Date | string | null;
};

function serialize(row: TaskRow) {
  return {
    id: row.id,
    field_id: row.field_id,
    field_name: row.field_name,
    title: row.title,
    scheduled_at: row.scheduled_at,
    status: row.status,
    task_kind: row.task_kind,
    notes: row.notes,
    completion: row.completed_domain_record_id ? {
      domain_type: row.completed_domain_type,
      domain_record_id: row.completed_domain_record_id,
      completed_at: row.completed_at,
    } : null,
  };
}

async function executedRecordMatchesField(
  database: DatabaseClient,
  workspaceId: string,
  fieldId: string,
  domainType: z.infer<typeof completeSchema>['domain_type'],
  recordId: string,
) {
  let result;
  switch (domainType) {
    case 'treatment':
      result = await sql`SELECT 1 FROM treatment_records WHERE id=${recordId}::uuid AND workspace_id=${workspaceId}::uuid AND field_id=${fieldId}::uuid LIMIT 1`.execute(database);
      break;
    case 'irrigation':
      result = await sql`SELECT 1 FROM irrigation_records WHERE id=${recordId}::uuid AND workspace_id=${workspaceId}::uuid AND field_id=${fieldId}::uuid LIMIT 1`.execute(database);
      break;
    case 'fertilization':
      result = await sql`SELECT 1 FROM fertilization_records WHERE id=${recordId}::uuid AND workspace_id=${workspaceId}::uuid AND field_id=${fieldId}::uuid LIMIT 1`.execute(database);
      break;
    case 'pruning':
      result = await sql`SELECT 1 FROM pruning_records WHERE id=${recordId}::uuid AND workspace_id=${workspaceId}::uuid AND field_id=${fieldId}::uuid LIMIT 1`.execute(database);
      break;
    case 'observation':
      result = await sql`SELECT 1 FROM observation_records WHERE id=${recordId}::uuid AND workspace_id=${workspaceId}::uuid AND field_id=${fieldId}::uuid LIMIT 1`.execute(database);
      break;
    case 'harvest_delivery':
      result = await sql`SELECT 1 FROM harvest_delivery_fields hdf JOIN harvest_deliveries hd ON hd.id=hdf.delivery_id WHERE hd.id=${recordId}::uuid AND hd.workspace_id=${workspaceId}::uuid AND hdf.field_id=${fieldId}::uuid LIMIT 1`.execute(database);
      break;
    case 'work':
      result = await sql`SELECT 1 FROM work_records WHERE id=${recordId}::uuid AND workspace_id=${workspaceId}::uuid AND field_id=${fieldId}::uuid LIMIT 1`.execute(database);
      break;
  }
  return Boolean(result?.rows[0]);
}

export function registerPlannedTaskRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.post('/api/v1/fields/:fieldId/planned-tasks', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !uuidPattern.test(fieldId)) return reply.code(404).send({ error: 'field_not_found' });
    if (!await fieldBelongsToWorkspace(database, fieldId, context.workspaceId)) return reply.code(404).send({ error: 'field_not_found' });

    const parsed = createSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_planned_task', issues: parsed.error.issues });
    const input = parsed.data;
    const id = randomUUID();
    const result = await sql<TaskRow>`
      INSERT INTO scheduled_events (
        id, workspace_id, field_id, source_domain_type, title, scheduled_at, status, source,
        task_kind, notes, created_by
      ) VALUES (
        ${id}::uuid, ${context.workspaceId}::uuid, ${fieldId}::uuid, ${input.task_kind}, ${input.title},
        ${input.scheduled_at}::timestamptz, 'planned', 'manual', ${input.task_kind}, ${input.notes ?? null}, ${context.userId}::uuid
      )
      RETURNING id, field_id, (SELECT name FROM fields WHERE id=${fieldId}::uuid) AS field_name,
                title, scheduled_at, status, task_kind, notes, completed_domain_type,
                completed_domain_record_id, completed_at
    `.execute(database);
    return reply.code(201).send(serialize(result.rows[0]!));
  });

  app.patch('/api/v1/planned-tasks/:taskId', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const taskId = (request.params as { taskId?: string }).taskId;
    if (!taskId || !uuidPattern.test(taskId)) return reply.code(404).send({ error: 'planned_task_not_found' });
    const parsed = updateSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_planned_task_update', issues: parsed.error.issues });
    const input = parsed.data;

    const result = await sql<TaskRow>`
      UPDATE scheduled_events se
      SET scheduled_at = COALESCE(${input.scheduled_at ?? null}::timestamptz, se.scheduled_at),
          title = COALESCE(${input.title ?? null}, se.title),
          notes = CASE WHEN ${Object.prototype.hasOwnProperty.call(input, 'notes')} THEN ${input.notes ?? null} ELSE se.notes END,
          status = COALESCE(${input.status ?? null}, se.status),
          updated_at = now()
      FROM fields f
      WHERE se.id=${taskId}::uuid
        AND se.workspace_id=${context.workspaceId}::uuid
        AND se.field_id=f.id AND f.workspace_id=se.workspace_id
        AND se.status <> 'completed'
      RETURNING se.id, se.field_id, f.name AS field_name, se.title, se.scheduled_at, se.status,
                se.task_kind, se.notes, se.completed_domain_type, se.completed_domain_record_id, se.completed_at
    `.execute(database);
    const row = result.rows[0];
    if (!row) return reply.code(404).send({ error: 'planned_task_not_found_or_completed' });
    return reply.send(serialize(row));
  });

  app.post('/api/v1/planned-tasks/:taskId/complete', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const taskId = (request.params as { taskId?: string }).taskId;
    if (!taskId || !uuidPattern.test(taskId)) return reply.code(404).send({ error: 'planned_task_not_found' });
    const parsed = completeSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_completion', issues: parsed.error.issues });

    const taskResult = await sql<{ field_id: string; status: string }>`
      SELECT field_id, status FROM scheduled_events
      WHERE id=${taskId}::uuid AND workspace_id=${context.workspaceId}::uuid AND field_id IS NOT NULL
      LIMIT 1
    `.execute(database);
    const task = taskResult.rows[0];
    if (!task) return reply.code(404).send({ error: 'planned_task_not_found' });
    if (task.status === 'completed') return reply.code(409).send({ error: 'planned_task_already_completed' });

    const matches = await executedRecordMatchesField(database, context.workspaceId, task.field_id, parsed.data.domain_type, parsed.data.domain_record_id);
    if (!matches) return reply.code(409).send({ error: 'completion_record_does_not_match_task_field' });

    const result = await sql<TaskRow>`
      UPDATE scheduled_events se
      SET status='completed', completed_domain_type=${parsed.data.domain_type},
          completed_domain_record_id=${parsed.data.domain_record_id}::uuid, completed_at=now(), updated_at=now()
      FROM fields f
      WHERE se.id=${taskId}::uuid AND se.workspace_id=${context.workspaceId}::uuid
        AND se.field_id=f.id AND f.workspace_id=se.workspace_id
      RETURNING se.id, se.field_id, f.name AS field_name, se.title, se.scheduled_at, se.status,
                se.task_kind, se.notes, se.completed_domain_type, se.completed_domain_record_id, se.completed_at
    `.execute(database);
    return reply.send(serialize(result.rows[0]!));
  });
}
