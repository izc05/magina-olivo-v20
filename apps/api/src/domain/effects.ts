import { randomUUID } from 'node:crypto';
import type { Transaction } from 'kysely';
import type { Database } from '../db/types.js';

type ProjectionInput = {
  workspaceId: string;
  fieldId: string;
  campaignId?: string | null;
  domainType: string;
  domainRecordId: string;
  occurredAt: string;
  title: string;
  summary?: string | null;
  iconKey?: string | null;
  cost?: {
    amountEur: number;
    category: string;
  };
  followUp?: {
    scheduledAt: string;
    title: string;
  };
};

export async function writeDomainEffects(trx: Transaction<Database>, input: ProjectionInput) {
  const timeline = await trx
    .insertInto('farm_timeline_projection')
    .values({
      workspace_id: input.workspaceId,
      field_id: input.fieldId,
      occurred_at: input.occurredAt,
      domain_type: input.domainType,
      domain_record_id: input.domainRecordId,
      title: input.title,
      summary: input.summary ?? null,
      icon_key: input.iconKey ?? null,
    })
    .onConflict((oc) => oc.columns(['field_id', 'domain_type', 'domain_record_id']).doUpdateSet({
      occurred_at: input.occurredAt,
      title: input.title,
      summary: input.summary ?? null,
      icon_key: input.iconKey ?? null,
    }))
    .returning('id')
    .executeTakeFirstOrThrow();

  let costId: string | null = null;
  if (input.cost) {
    const cost = await trx
      .insertInto('cost_ledger_projection')
      .values({
        workspace_id: input.workspaceId,
        field_id: input.fieldId,
        campaign_id: input.campaignId ?? null,
        occurred_on: input.occurredAt.slice(0, 10),
        domain_type: input.domainType,
        domain_record_id: input.domainRecordId,
        category: input.cost.category,
        amount_eur: input.cost.amountEur,
      })
      .onConflict((oc) => oc.columns(['domain_type', 'domain_record_id']).doUpdateSet({
        occurred_on: input.occurredAt.slice(0, 10),
        category: input.cost!.category,
        amount_eur: input.cost!.amountEur,
      }))
      .returning('id')
      .executeTakeFirstOrThrow();
    costId = cost.id;
  }

  let scheduledEventId: string | null = null;
  if (input.followUp) {
    scheduledEventId = randomUUID();
    await trx.insertInto('scheduled_events').values({
      id: scheduledEventId,
      workspace_id: input.workspaceId,
      field_id: input.fieldId,
      source_domain_type: input.domainType,
      source_domain_record_id: input.domainRecordId,
      title: input.followUp.title,
      scheduled_at: input.followUp.scheduledAt,
      status: 'planned',
      source: 'domain_followup',
    }).execute();
  }

  return {
    timelineId: timeline.id,
    costId,
    scheduledEventId,
  };
}
