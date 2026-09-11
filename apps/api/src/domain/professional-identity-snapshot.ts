import { sql } from 'kysely';
import type { DatabaseClient } from '../db/client.js';

export async function loadProfessionalIdentitySnapshots(db: DatabaseClient, workspaceId: string, customerId: string) {
  const issuerResult = await sql`
    SELECT jsonb_build_object(
      'workspace_id', w.id,
      'workspace_name', w.name,
      'legal_name', COALESCE(NULLIF(pbp.legal_name, ''), w.name),
      'tax_id', pbp.tax_id,
      'address', pbp.address,
      'postal_code', pbp.postal_code,
      'municipality', pbp.municipality,
      'province', pbp.province,
      'email', pbp.email,
      'phone', pbp.phone,
      'payment_terms', pbp.payment_terms,
      'footer_note', pbp.footer_note
    ) AS snapshot
    FROM workspaces w
    LEFT JOIN professional_business_profiles pbp ON pbp.workspace_id = w.id
    WHERE w.id = ${workspaceId}::uuid
  `.execute(db);

  const customerResult = await sql`
    SELECT jsonb_build_object(
      'id', p.id,
      'display_name', p.display_name,
      'legal_name', p.legal_name,
      'tax_id', p.tax_id,
      'phone', p.phone,
      'email', p.email
    ) AS snapshot
    FROM parties p
    WHERE p.id = ${customerId}::uuid
      AND p.workspace_id = ${workspaceId}::uuid
      AND p.active = TRUE
  `.execute(db);

  return {
    issuer: issuerResult.rows[0]?.snapshot ?? null,
    customer: customerResult.rows[0]?.snapshot ?? null,
  };
}
