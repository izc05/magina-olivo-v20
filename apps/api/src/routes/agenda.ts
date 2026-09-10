import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const weatherSensitiveDomainTypes = new Set(['treatment', 'irrigation', 'pruning', 'harvest', 'harvest_delivery', 'work']);

function dateOnly(value: Date | string) {
  return typeof value === 'string' ? value.slice(0, 10) : value.toISOString().slice(0, 10);
}

export function registerAgendaRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/agenda/today', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const query = request.query as { date?: string };
    const requestedDate = query.date?.trim();
    if (requestedDate && !/^\d{4}-\d{2}-\d{2}$/.test(requestedDate)) {
      return reply.code(400).send({ error: 'invalid_date' });
    }
    const targetDate = requestedDate ?? new Date().toISOString().slice(0, 10);

    const result = await sql<{
      id: string;
      field_id: string | null;
      field_name: string | null;
      source_domain_type: string | null;
      source_domain_record_id: string | null;
      task_kind: string | null;
      notes: string | null;
      title: string;
      scheduled_at: string;
      status: string;
      source: string;
    }>`
      SELECT se.id, se.field_id, f.name AS field_name, se.source_domain_type, se.source_domain_record_id,
             se.task_kind, se.notes, se.title, se.scheduled_at, se.status, se.source
      FROM scheduled_events se
      LEFT JOIN fields f ON f.id = se.field_id AND f.workspace_id = se.workspace_id
      WHERE se.workspace_id = ${context.workspaceId}::uuid
        AND se.status IN ('planned','postponed')
        AND se.scheduled_at < (${targetDate}::date + interval '8 days')
      ORDER BY se.scheduled_at ASC
    `.execute(database);

    const items = result.rows.map((row) => {
      const scheduledDate = dateOnly(row.scheduled_at);
      const overdue = scheduledDate < targetDate;
      const today = scheduledDate === targetDate;
      const weatherSensitive = row.source_domain_type ? weatherSensitiveDomainTypes.has(row.source_domain_type) : false;
      return {
        id: row.id,
        field_id: row.field_id,
        field_name: row.field_name,
        source_domain_type: row.source_domain_type,
        source_domain_record_id: row.source_domain_record_id,
        task_kind: row.task_kind,
        notes: row.notes,
        title: row.title,
        scheduled_at: row.scheduled_at,
        status: row.status,
        source: row.source,
        bucket: overdue ? 'overdue' : today ? 'today' : 'upcoming',
        priority: overdue ? 'high' : today ? 'normal' : 'low',
        weather_sensitive: weatherSensitive,
        weather_context_url: weatherSensitive && row.field_id ? `/api/v1/fields/${row.field_id}/weather/daily` : null,
      };
    });

    return {
      date: targetDate,
      overdue: items.filter((item) => item.bucket === 'overdue'),
      today: items.filter((item) => item.bucket === 'today'),
      upcoming: items.filter((item) => item.bucket === 'upcoming'),
      counts: {
        overdue: items.filter((item) => item.bucket === 'overdue').length,
        today: items.filter((item) => item.bucket === 'today').length,
        upcoming: items.filter((item) => item.bucket === 'upcoming').length,
        weather_sensitive: items.filter((item) => item.weather_sensitive).length,
      },
      rule: 'Weather context may warn or reprioritize a task, but never completes or cancels it automatically.',
    };
  });
}
