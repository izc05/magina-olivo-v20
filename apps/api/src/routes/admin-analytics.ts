import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import { auditAdminAction, requirePlatformAccess } from '../admin/access.js';
import type { DatabaseClient } from '../db/client.js';

const analyticsQuerySchema = z.object({
  days: z.coerce.number().int().refine((value) => value === 30 || value === 90, 'unsupported_window').default(30),
});

type AnalyticsPoint = {
  day: string;
  users_new: number;
  workspaces_new: number;
  works: number;
  harvest_kg: number;
  expenses_eur: number;
  invoiced_eur: number;
  admin_actions: number;
};

async function readAnalytics(db: DatabaseClient, days: number): Promise<AnalyticsPoint[]> {
  const result = await sql<AnalyticsPoint>`
    WITH days AS (
      SELECT generate_series(current_date - (${days}::int - 1), current_date, interval '1 day')::date AS day
    ),
    users_daily AS (
      SELECT created_at::date AS day, count(*)::int AS value
      FROM users
      WHERE status <> 'deleted' AND created_at::date >= current_date - (${days}::int - 1)
      GROUP BY 1
    ),
    workspaces_daily AS (
      SELECT created_at::date AS day, count(*)::int AS value
      FROM workspaces
      WHERE created_at::date >= current_date - (${days}::int - 1)
      GROUP BY 1
    ),
    works_daily AS (
      SELECT occurred_on::date AS day, count(*)::int AS value
      FROM work_records
      WHERE occurred_on >= current_date - (${days}::int - 1)
      GROUP BY 1
    ),
    harvest_daily AS (
      SELECT delivery_at::date AS day, coalesce(sum(total_kg), 0)::float8 AS value
      FROM harvest_deliveries
      WHERE delivery_at::date >= current_date - (${days}::int - 1)
      GROUP BY 1
    ),
    expenses_daily AS (
      SELECT occurred_on::date AS day, coalesce(sum(amount_eur), 0)::float8 AS value
      FROM expense_records
      WHERE occurred_on >= current_date - (${days}::int - 1)
      GROUP BY 1
    ),
    invoices_daily AS (
      SELECT issued_on::date AS day, coalesce(sum(total_eur), 0)::float8 AS value
      FROM professional_invoices
      WHERE status = 'issued'
        AND issued_on IS NOT NULL
        AND issued_on >= current_date - (${days}::int - 1)
      GROUP BY 1
    ),
    admin_daily AS (
      SELECT created_at::date AS day, count(*)::int AS value
      FROM admin_audit_log
      WHERE created_at::date >= current_date - (${days}::int - 1)
      GROUP BY 1
    )
    SELECT
      d.day::text AS day,
      coalesce(u.value, 0)::int AS users_new,
      coalesce(w.value, 0)::int AS workspaces_new,
      coalesce(j.value, 0)::int AS works,
      coalesce(h.value, 0)::float8 AS harvest_kg,
      coalesce(e.value, 0)::float8 AS expenses_eur,
      coalesce(i.value, 0)::float8 AS invoiced_eur,
      coalesce(a.value, 0)::int AS admin_actions
    FROM days d
    LEFT JOIN users_daily u ON u.day = d.day
    LEFT JOIN workspaces_daily w ON w.day = d.day
    LEFT JOIN works_daily j ON j.day = d.day
    LEFT JOIN harvest_daily h ON h.day = d.day
    LEFT JOIN expenses_daily e ON e.day = d.day
    LEFT JOIN invoices_daily i ON i.day = d.day
    LEFT JOIN admin_daily a ON a.day = d.day
    ORDER BY d.day ASC
  `.execute(db);
  return result.rows;
}

function summarize(points: AnalyticsPoint[]) {
  return points.reduce((summary, point) => ({
    users_new: summary.users_new + point.users_new,
    workspaces_new: summary.workspaces_new + point.workspaces_new,
    works: summary.works + point.works,
    harvest_kg: summary.harvest_kg + point.harvest_kg,
    expenses_eur: summary.expenses_eur + point.expenses_eur,
    invoiced_eur: summary.invoiced_eur + point.invoiced_eur,
    admin_actions: summary.admin_actions + point.admin_actions,
  }), {
    users_new: 0,
    workspaces_new: 0,
    works: 0,
    harvest_kg: 0,
    expenses_eur: 0,
    invoiced_eur: 0,
    admin_actions: 0,
  });
}

function csvCell(value: string | number) {
  let text = String(value);
  if (/^[=+\-@]/.test(text)) text = `'${text}`;
  return `"${text.replaceAll('"', '""')}"`;
}

function analyticsCsv(points: AnalyticsPoint[]) {
  const headers = ['fecha', 'usuarios_nuevos', 'workspaces_nuevos', 'trabajos', 'cosecha_kg', 'gastos_eur', 'facturado_eur', 'acciones_admin'];
  const rows = points.map((point) => [
    point.day,
    point.users_new,
    point.workspaces_new,
    point.works,
    point.harvest_kg,
    point.expenses_eur,
    point.invoiced_eur,
    point.admin_actions,
  ].map(csvCell).join(','));
  return `\uFEFF${headers.map(csvCell).join(',')}\n${rows.join('\n')}\n`;
}

export function registerAdminAnalyticsRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/admin/analytics/timeseries', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db);
    if (!auth) return;
    const query = analyticsQuerySchema.safeParse(request.query);
    if (!query.success) return reply.code(400).send({ error: 'validation_error' });
    const points = await readAnalytics(auth.database, query.data.days);
    return {
      days: query.data.days,
      generated_at: new Date().toISOString(),
      summary: summarize(points),
      points,
    };
  });

  app.get('/api/v1/admin/analytics/export.csv', async (request, reply) => {
    const auth = await requirePlatformAccess(request, reply, db, 'admin');
    if (!auth) return;
    const query = analyticsQuerySchema.safeParse(request.query);
    if (!query.success) return reply.code(400).send({ error: 'validation_error' });
    const points = await readAnalytics(auth.database, query.data.days);
    await auditAdminAction(auth.database, auth.access, 'analytics.exported', 'analytics', 'operations_timeseries', {
      days: query.data.days,
      rows: points.length,
      format: 'csv',
      aggregate_only: true,
    });
    reply.header('content-disposition', `attachment; filename="magina-admin-analytics-${query.data.days}d.csv"`);
    reply.type('text/csv; charset=utf-8');
    return reply.send(analyticsCsv(points));
  });
}
