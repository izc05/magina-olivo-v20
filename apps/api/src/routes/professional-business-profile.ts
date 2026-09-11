import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { z } from 'zod';
import type { DatabaseClient } from '../db/client.js';
import { requireContext, requireDatabase } from '../http/helpers.js';

const profileSchema = z.object({
  legal_name: z.string().trim().max(300).optional().nullable(),
  tax_id: z.string().trim().max(60).optional().nullable(),
  address: z.string().trim().max(500).optional().nullable(),
  postal_code: z.string().trim().max(30).optional().nullable(),
  municipality: z.string().trim().max(160).optional().nullable(),
  province: z.string().trim().max(160).optional().nullable(),
  email: z.string().trim().email().max(320).optional().nullable(),
  phone: z.string().trim().max(60).optional().nullable(),
  payment_terms: z.string().trim().max(2000).optional().nullable(),
  footer_note: z.string().trim().max(2000).optional().nullable(),
});

export function registerProfessionalBusinessProfileRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/professional/business-profile', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;

    const result = await sql`
      SELECT w.id AS workspace_id, w.name AS workspace_name,
             pbp.legal_name, pbp.tax_id, pbp.address, pbp.postal_code,
             pbp.municipality, pbp.province, pbp.email, pbp.phone,
             pbp.payment_terms, pbp.footer_note, pbp.updated_at
      FROM workspaces w
      LEFT JOIN professional_business_profiles pbp ON pbp.workspace_id = w.id
      WHERE w.id = ${context.workspaceId}::uuid
    `.execute(database);
    const row = result.rows[0];
    if (!row) return reply.code(404).send({ error: 'workspace_not_found' });
    return { profile: row };
  });

  app.put('/api/v1/professional/business-profile', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const parsed = profileSchema.safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_business_profile', issues: parsed.error.issues });
    const value = parsed.data;

    const result = await sql`
      INSERT INTO professional_business_profiles (
        workspace_id, legal_name, tax_id, address, postal_code, municipality, province,
        email, phone, payment_terms, footer_note, updated_by, updated_at
      ) VALUES (
        ${context.workspaceId}::uuid, ${value.legal_name ?? null}, ${value.tax_id ?? null},
        ${value.address ?? null}, ${value.postal_code ?? null}, ${value.municipality ?? null},
        ${value.province ?? null}, ${value.email ?? null}, ${value.phone ?? null},
        ${value.payment_terms ?? null}, ${value.footer_note ?? null}, ${context.userId}::uuid, now()
      )
      ON CONFLICT (workspace_id)
      DO UPDATE SET
        legal_name = EXCLUDED.legal_name,
        tax_id = EXCLUDED.tax_id,
        address = EXCLUDED.address,
        postal_code = EXCLUDED.postal_code,
        municipality = EXCLUDED.municipality,
        province = EXCLUDED.province,
        email = EXCLUDED.email,
        phone = EXCLUDED.phone,
        payment_terms = EXCLUDED.payment_terms,
        footer_note = EXCLUDED.footer_note,
        updated_by = EXCLUDED.updated_by,
        updated_at = now()
      RETURNING *
    `.execute(database);
    return { profile: result.rows[0] };
  });
}
