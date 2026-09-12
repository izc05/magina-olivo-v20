import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { parseBody, requireContext, requireDatabase } from '../http/helpers.js';

const preferenceSchema = z.object({
  economic_weight: z.enum(['normal', 'reduced']).default('normal'),
  document_weight: z.enum(['normal', 'reduced']).default('normal'),
  show_low_priority: z.boolean().default(true),
});

type PreferenceRow = {
  economic_weight: 'normal' | 'reduced';
  document_weight: 'normal' | 'reduced';
  show_low_priority: boolean;
  updated_at: Date | string;
};

const defaults = {
  economic_weight: 'normal' as const,
  document_weight: 'normal' as const,
  show_low_priority: true,
};

export function registerHomePriorityPreferenceRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/home-priority/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const result = await sql<PreferenceRow>`
      SELECT economic_weight, document_weight, show_low_priority, updated_at
      FROM home_priority_preferences
      WHERE user_id = ${context.userId}::uuid
        AND workspace_id = ${context.workspaceId}::uuid
    `.execute(database);

    const row = result.rows[0];
    return {
      preferences: row ? {
        economic_weight: row.economic_weight,
        document_weight: row.document_weight,
        show_low_priority: row.show_low_priority,
      } : defaults,
      protected_categories: ['overdue_task', 'agronomy_avoid'],
      rule: 'home-priority-preferences-v1',
    };
  });

  app.put('/api/v1/home-priority/preferences', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(preferenceSchema, request.body, reply);
    if (!input) return;

    const saved = await sql<PreferenceRow>`
      INSERT INTO home_priority_preferences (
        user_id, workspace_id, economic_weight, document_weight, show_low_priority, updated_at
      ) VALUES (
        ${context.userId}::uuid,
        ${context.workspaceId}::uuid,
        ${input.economic_weight},
        ${input.document_weight},
        ${input.show_low_priority},
        now()
      )
      ON CONFLICT (user_id, workspace_id)
      DO UPDATE SET
        economic_weight = EXCLUDED.economic_weight,
        document_weight = EXCLUDED.document_weight,
        show_low_priority = EXCLUDED.show_low_priority,
        updated_at = now()
      RETURNING economic_weight, document_weight, show_low_priority, updated_at
    `.execute(database);

    const row = saved.rows[0];
    return {
      preferences: {
        economic_weight: row.economic_weight,
        document_weight: row.document_weight,
        show_low_priority: row.show_low_priority,
      },
      protected_categories: ['overdue_task', 'agronomy_avoid'],
      rule: 'home-priority-preferences-v1',
    };
  });
}
