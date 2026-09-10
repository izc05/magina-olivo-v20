import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { sql } from 'kysely';
import { createCrewSchema, createCustomerSiteSchema, createMachinerySchema, createMaterialSchema, createPartySchema, createWorkSchema } from '@magina/contracts';
import type { DatabaseClient } from '../db/client.js';
import { writeDomainEffects } from '../domain/effects.js';
import { fieldBelongsToWorkspace, parseBody, requireContext, requireDatabase } from '../http/helpers.js';

async function partyBelongsToWorkspace(db: DatabaseClient, partyId: string, workspaceId: string) {
  const result = await sql<{ id: string }>`SELECT id FROM parties WHERE id = ${partyId}::uuid AND workspace_id = ${workspaceId}::uuid AND active = TRUE`.execute(db);
  return Boolean(result.rows[0]);
}

async function customerSiteBelongsToWorkspace(db: DatabaseClient, siteId: string, workspaceId: string, customerPartyId?: string) {
  const result = await sql<{ id: string; customer_party_id: string }>`
    SELECT id, customer_party_id FROM customer_sites
    WHERE id = ${siteId}::uuid AND workspace_id = ${workspaceId}::uuid AND active = TRUE
  `.execute(db);
  const site = result.rows[0];
  return Boolean(site && (!customerPartyId || site.customer_party_id === customerPartyId));
}

async function crewBelongsToWorkspace(db: DatabaseClient, crewId: string, workspaceId: string) {
  const result = await sql<{ id: string }>`SELECT id FROM crews WHERE id = ${crewId}::uuid AND workspace_id = ${workspaceId}::uuid AND active = TRUE`.execute(db);
  return Boolean(result.rows[0]);
}

async function machineryBelongsToWorkspace(db: DatabaseClient, id: string, workspaceId: string) {
  const result = await sql<{ id: string }>`SELECT id FROM machinery WHERE id = ${id}::uuid AND workspace_id = ${workspaceId}::uuid AND active = TRUE`.execute(db);
  return Boolean(result.rows[0]);
}

async function materialBelongsToWorkspace(db: DatabaseClient, id: string, workspaceId: string) {
  const result = await sql<{ id: string }>`SELECT id FROM materials WHERE id = ${id}::uuid AND workspace_id = ${workspaceId}::uuid AND active = TRUE`.execute(db);
  return Boolean(result.rows[0]);
}

export function registerWorkRoutes(app: FastifyInstance, db: DatabaseClient | null) {
  app.get('/api/v1/parties', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const result = await sql`SELECT * FROM parties WHERE workspace_id = ${context.workspaceId}::uuid AND active = TRUE ORDER BY display_name`.execute(database);
    return { parties: result.rows };
  });

  app.post('/api/v1/parties', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createPartySchema, request.body, reply);
    if (!input) return;

    const replay = await sql`SELECT * FROM parties WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, party: replay.rows[0] });

    const id = input.entity_id ?? randomUUID();
    const result = await sql`
      INSERT INTO parties (id, workspace_id, client_operation_id, kind, display_name, legal_name, tax_id, phone, email, roles, notes)
      VALUES (${id}::uuid, ${context.workspaceId}::uuid, ${input.client_operation_id}::uuid, ${input.kind}, ${input.display_name}, ${input.legal_name ?? null}, ${input.tax_id ?? null}, ${input.phone ?? null}, ${input.email ?? null}, ${input.roles}::text[], ${input.notes ?? null})
      RETURNING *
    `.execute(database);
    return reply.code(201).send({ replayed: false, party: result.rows[0] });
  });

  app.get('/api/v1/customer-sites', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const query = request.query as { customer_party_id?: string };
    const result = query.customer_party_id
      ? await sql`SELECT cs.*, p.display_name AS customer_name FROM customer_sites cs JOIN parties p ON p.id = cs.customer_party_id WHERE cs.workspace_id = ${context.workspaceId}::uuid AND cs.customer_party_id = ${query.customer_party_id}::uuid AND cs.active = TRUE ORDER BY cs.name`.execute(database)
      : await sql`SELECT cs.*, p.display_name AS customer_name FROM customer_sites cs JOIN parties p ON p.id = cs.customer_party_id WHERE cs.workspace_id = ${context.workspaceId}::uuid AND cs.active = TRUE ORDER BY p.display_name, cs.name`.execute(database);
    return { customer_sites: result.rows };
  });

  app.post('/api/v1/customer-sites', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createCustomerSiteSchema, request.body, reply);
    if (!input) return;
    if (!await partyBelongsToWorkspace(database, input.customer_party_id, context.workspaceId)) return reply.code(404).send({ error: 'customer_party_not_found' });
    if (input.canonical_field_id && !await fieldBelongsToWorkspace(database, input.canonical_field_id, context.workspaceId)) return reply.code(404).send({ error: 'canonical_field_not_found' });

    const replay = await sql`SELECT * FROM customer_sites WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, customer_site: replay.rows[0] });

    const id = input.entity_id ?? randomUUID();
    const result = await sql`
      INSERT INTO customer_sites (id, workspace_id, client_operation_id, customer_party_id, name, municipality, address, external_reference, canonical_field_id, notes)
      VALUES (${id}::uuid, ${context.workspaceId}::uuid, ${input.client_operation_id}::uuid, ${input.customer_party_id}::uuid, ${input.name}, ${input.municipality ?? null}, ${input.address ?? null}, ${input.external_reference ?? null}, ${input.canonical_field_id ?? null}::uuid, ${input.notes ?? null})
      RETURNING *
    `.execute(database);
    return reply.code(201).send({ replayed: false, customer_site: result.rows[0] });
  });

  app.get('/api/v1/crews', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const crews = await sql`SELECT * FROM crews WHERE workspace_id = ${context.workspaceId}::uuid AND active = TRUE ORDER BY name`.execute(database);
    const crewIds = crews.rows.map((row) => (row as { id: string }).id);
    if (!crewIds.length) return { crews: [] };
    const members = await sql`SELECT cm.*, p.display_name FROM crew_members cm JOIN parties p ON p.id = cm.party_id WHERE cm.crew_id = ANY(${crewIds}::uuid[]) AND cm.active = TRUE ORDER BY p.display_name`.execute(database);
    return {
      crews: crews.rows.map((row) => {
        const id = (row as { id: string }).id;
        return { ...row as Record<string, unknown>, members: members.rows.filter((member) => (member as { crew_id: string }).crew_id === id) };
      }),
    };
  });

  app.post('/api/v1/crews', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createCrewSchema, request.body, reply);
    if (!input) return;

    if (input.leader_party_id && !await partyBelongsToWorkspace(database, input.leader_party_id, context.workspaceId)) return reply.code(404).send({ error: 'leader_party_not_found' });
    for (const memberId of input.member_party_ids) {
      if (!await partyBelongsToWorkspace(database, memberId, context.workspaceId)) return reply.code(404).send({ error: 'crew_member_not_found', party_id: memberId });
    }

    const replay = await sql`SELECT * FROM crews WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, crew: replay.rows[0] });

    const id = input.entity_id ?? randomUUID();
    const saved = await database.transaction().execute(async (trx) => {
      const crew = await sql`
        INSERT INTO crews (id, workspace_id, client_operation_id, name, leader_party_id, default_rate_eur, default_rate_unit, notes)
        VALUES (${id}::uuid, ${context.workspaceId}::uuid, ${input.client_operation_id}::uuid, ${input.name}, ${input.leader_party_id ?? null}::uuid, ${input.default_rate_eur ?? null}, ${input.default_rate_unit ?? null}, ${input.notes ?? null}) RETURNING *
      `.execute(trx);
      for (const memberId of input.member_party_ids) {
        await sql`INSERT INTO crew_members (crew_id, party_id) VALUES (${id}::uuid, ${memberId}::uuid)`.execute(trx);
      }
      return crew.rows[0];
    });
    return reply.code(201).send({ replayed: false, crew: saved });
  });

  app.get('/api/v1/machinery', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const result = await sql`SELECT * FROM machinery WHERE workspace_id = ${context.workspaceId}::uuid AND active = TRUE ORDER BY name`.execute(database);
    return { machinery: result.rows };
  });

  app.post('/api/v1/machinery', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createMachinerySchema, request.body, reply);
    if (!input) return;
    if (input.owner_party_id && !await partyBelongsToWorkspace(database, input.owner_party_id, context.workspaceId)) return reply.code(404).send({ error: 'owner_party_not_found' });

    const id = input.entity_id ?? randomUUID();
    const replay = await sql`SELECT * FROM machinery WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, machinery: replay.rows[0] });
    const result = await sql`
      INSERT INTO machinery (id, workspace_id, client_operation_id, name, category, ownership, owner_party_id, registration_or_serial, default_rate_eur, default_rate_unit, notes)
      VALUES (${id}::uuid, ${context.workspaceId}::uuid, ${input.client_operation_id}::uuid, ${input.name}, ${input.category ?? null}, ${input.ownership}, ${input.owner_party_id ?? null}::uuid, ${input.registration_or_serial ?? null}, ${input.default_rate_eur ?? null}, ${input.default_rate_unit ?? null}, ${input.notes ?? null}) RETURNING *
    `.execute(database);
    return reply.code(201).send({ replayed: false, machinery: result.rows[0] });
  });

  app.get('/api/v1/materials', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const result = await sql`SELECT * FROM materials WHERE workspace_id = ${context.workspaceId}::uuid AND active = TRUE ORDER BY name`.execute(database);
    return { materials: result.rows };
  });

  app.post('/api/v1/materials', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createMaterialSchema, request.body, reply);
    if (!input) return;
    if (input.supplier_party_id && !await partyBelongsToWorkspace(database, input.supplier_party_id, context.workspaceId)) return reply.code(404).send({ error: 'supplier_party_not_found' });

    const id = input.entity_id ?? randomUUID();
    const replay = await sql`SELECT * FROM materials WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, material: replay.rows[0] });
    const result = await sql`
      INSERT INTO materials (id, workspace_id, client_operation_id, name, category, default_unit, default_unit_cost_eur, supplier_party_id, notes)
      VALUES (${id}::uuid, ${context.workspaceId}::uuid, ${input.client_operation_id}::uuid, ${input.name}, ${input.category ?? null}, ${input.default_unit ?? null}, ${input.default_unit_cost_eur ?? null}, ${input.supplier_party_id ?? null}::uuid, ${input.notes ?? null}) RETURNING *
    `.execute(database);
    return reply.code(201).send({ replayed: false, material: result.rows[0] });
  });

  app.get('/api/v1/fields/:fieldId/works', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const fieldId = (request.params as { fieldId?: string }).fieldId;
    if (!fieldId || !await fieldBelongsToWorkspace(database, fieldId, context.workspaceId)) return reply.code(404).send({ error: 'field_not_found' });

    const works = await sql`SELECT * FROM work_records WHERE workspace_id = ${context.workspaceId}::uuid AND field_id = ${fieldId}::uuid ORDER BY occurred_on DESC, created_at DESC LIMIT 200`.execute(database);
    const ids = works.rows.map((row) => (row as { id: string }).id);
    if (!ids.length) return { works: [] };
    const participants = await sql`SELECT * FROM work_participants WHERE work_id = ANY(${ids}::uuid[]) ORDER BY created_at`.execute(database);
    const resources = await sql`SELECT * FROM work_resources WHERE work_id = ANY(${ids}::uuid[]) ORDER BY created_at`.execute(database);
    return {
      works: works.rows.map((row) => {
        const id = (row as { id: string }).id;
        return {
          ...row as Record<string, unknown>,
          participants: participants.rows.filter((item) => (item as { work_id: string }).work_id === id),
          resources: resources.rows.filter((item) => (item as { work_id: string }).work_id === id),
        };
      }),
    };
  });

  app.get('/api/v1/customer-sites/:siteId/works', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const siteId = (request.params as { siteId?: string }).siteId;
    if (!siteId || !await customerSiteBelongsToWorkspace(database, siteId, context.workspaceId)) return reply.code(404).send({ error: 'customer_site_not_found' });
    const works = await sql`SELECT * FROM work_records WHERE workspace_id = ${context.workspaceId}::uuid AND customer_site_id = ${siteId}::uuid ORDER BY occurred_on DESC, created_at DESC LIMIT 200`.execute(database);
    return { works: works.rows };
  });

  app.post('/api/v1/works', async (request, reply) => {
    const context = requireContext(request, reply);
    const database = requireDatabase(db, reply);
    if (!context || !database) return;
    const input = parseBody(createWorkSchema, request.body, reply);
    if (!input) return;

    if (input.field_id && !await fieldBelongsToWorkspace(database, input.field_id, context.workspaceId)) return reply.code(404).send({ error: 'field_not_found' });
    if (input.customer_party_id && !await partyBelongsToWorkspace(database, input.customer_party_id, context.workspaceId)) return reply.code(404).send({ error: 'customer_party_not_found' });
    if (input.customer_site_id && !await customerSiteBelongsToWorkspace(database, input.customer_site_id, context.workspaceId, input.customer_party_id)) return reply.code(404).send({ error: 'customer_site_not_found' });

    for (const participant of input.participants) {
      if (participant.party_id && !await partyBelongsToWorkspace(database, participant.party_id, context.workspaceId)) return reply.code(404).send({ error: 'participant_party_not_found', party_id: participant.party_id });
      if (participant.crew_id && !await crewBelongsToWorkspace(database, participant.crew_id, context.workspaceId)) return reply.code(404).send({ error: 'participant_crew_not_found', crew_id: participant.crew_id });
    }
    for (const resource of input.resources) {
      if (resource.machinery_id && !await machineryBelongsToWorkspace(database, resource.machinery_id, context.workspaceId)) return reply.code(404).send({ error: 'machinery_not_found', machinery_id: resource.machinery_id });
      if (resource.material_id && !await materialBelongsToWorkspace(database, resource.material_id, context.workspaceId)) return reply.code(404).send({ error: 'material_not_found', material_id: resource.material_id });
      if (resource.supplier_party_id && !await partyBelongsToWorkspace(database, resource.supplier_party_id, context.workspaceId)) return reply.code(404).send({ error: 'supplier_party_not_found', party_id: resource.supplier_party_id });
    }

    const replay = await sql`SELECT * FROM work_records WHERE workspace_id = ${context.workspaceId}::uuid AND client_operation_id = ${input.client_operation_id}::uuid`.execute(database);
    if (replay.rows[0]) return reply.code(200).send({ replayed: true, work: replay.rows[0] });

    let campaignId: string | null = input.campaign_id ?? null;
    if (!campaignId) {
      const active = await database.selectFrom('campaigns').select('id').where('workspace_id', '=', context.workspaceId).where('status', '=', 'active').orderBy('start_date', 'desc').executeTakeFirst();
      campaignId = active?.id ?? null;
    }

    const workId = input.entity_id ?? randomUUID();
    const participantCost = input.participants.reduce((sum, item) => sum + (item.cost_eur ?? ((item.quantity ?? 0) * (item.rate_eur ?? 0))), 0);
    const resourceCost = input.resources.reduce((sum, item) => sum + (item.cost_eur ?? ((item.quantity ?? 0) * (item.unit_cost_eur ?? 0))), 0);
    const totalCost = participantCost + resourceCost;
    const initialPaymentStatus = input.performed_for === 'third-party'
      ? (input.payment_status ?? ((input.collected_eur ?? 0) > 0 ? 'partial' : 'pending'))
      : 'not-applicable';

    const saved = await database.transaction().execute(async (trx) => {
      const work = await sql`
        INSERT INTO work_records (
          id, workspace_id, field_id, customer_site_id, campaign_id, client_operation_id,
          type, occurred_on, title, notes, performed_for, customer_party_id,
          quoted_amount_eur, charge_eur, collected_eur, payment_status, invoice_reference, created_by
        ) VALUES (
          ${workId}::uuid, ${context.workspaceId}::uuid, ${input.field_id ?? null}::uuid, ${input.customer_site_id ?? null}::uuid,
          ${campaignId}::uuid, ${input.client_operation_id}::uuid, ${input.type}, ${input.occurred_on}::date, ${input.title}, ${input.notes ?? null},
          ${input.performed_for}, ${input.customer_party_id ?? null}::uuid, ${input.quoted_amount_eur ?? null}, ${input.charge_eur ?? null},
          ${input.collected_eur ?? null}, ${initialPaymentStatus}, ${input.invoice_reference ?? null}, ${context.userId}::uuid
        ) RETURNING *
      `.execute(trx);

      for (const participant of input.participants) {
        const calculatedCost = participant.cost_eur ?? ((participant.quantity ?? 0) * (participant.rate_eur ?? 0));
        await sql`INSERT INTO work_participants (work_id, party_id, crew_id, display_name, role, quantity, unit, rate_eur, cost_eur) VALUES (${workId}::uuid, ${participant.party_id ?? null}::uuid, ${participant.crew_id ?? null}::uuid, ${participant.display_name}, ${participant.role ?? null}, ${participant.quantity ?? null}, ${participant.unit ?? null}, ${participant.rate_eur ?? null}, ${calculatedCost || null})`.execute(trx);
      }
      for (const resource of input.resources) {
        const calculatedCost = resource.cost_eur ?? ((resource.quantity ?? 0) * (resource.unit_cost_eur ?? 0));
        await sql`INSERT INTO work_resources (work_id, kind, machinery_id, material_id, supplier_party_id, name, quantity, unit, unit_cost_eur, cost_eur) VALUES (${workId}::uuid, ${resource.kind}, ${resource.machinery_id ?? null}::uuid, ${resource.material_id ?? null}::uuid, ${resource.supplier_party_id ?? null}::uuid, ${resource.name}, ${resource.quantity ?? null}, ${resource.unit ?? null}, ${resource.unit_cost_eur ?? null}, ${calculatedCost || null})`.execute(trx);
      }

      const projection = input.field_id ? await writeDomainEffects(trx, {
        workspaceId: context.workspaceId,
        fieldId: input.field_id,
        campaignId,
        domainType: 'work',
        domainRecordId: workId,
        occurredAt: `${input.occurred_on}T12:00:00.000Z`,
        title: input.title,
        summary: [
          input.performed_for === 'third-party' ? 'Trabajo para tercero' : null,
          input.participants.length ? `${input.participants.length} participante${input.participants.length === 1 ? '' : 's'}` : null,
          input.resources.length ? `${input.resources.length} recurso${input.resources.length === 1 ? '' : 's'}` : null,
          totalCost > 0 ? `${totalCost.toFixed(2)} €` : null,
        ].filter(Boolean).join(' · ') || input.notes || null,
        iconKey: 'work',
        cost: totalCost > 0 ? { amountEur: totalCost, category: 'work' } : undefined,
      }) : null;

      return { work: work.rows[0], projection, total_cost_eur: totalCost };
    });

    return reply.code(201).send({ replayed: false, ...saved });
  });
}
