import { apiFetch } from '@/lib/api-client';

export type SupportedApiRecordSlug = 'riego' | 'tratamiento' | 'abono' | 'poda' | 'gasto';

export function supportsApiRecord(slug: string): slug is SupportedApiRecordSlug {
  return ['riego', 'tratamiento', 'abono', 'poda', 'gasto'].includes(slug);
}

function numberValue(value?: string) {
  if (!value) return undefined;
  const parsed = Number(value.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : undefined;
}

function occurredAt(date?: string) {
  return `${date ?? new Date().toISOString().slice(0, 10)}T12:00:00.000Z`;
}

function followUp(data: Record<string, string>) {
  const date = data.nextDate ?? data.reviewDate;
  if (!date) return undefined;
  const time = data.nextTime || '09:00';
  return { scheduled_at: `${date}T${time}:00.000Z` };
}

export async function saveApiRecord(input: {
  slug: SupportedApiRecordSlug;
  fieldId: string;
  workspaceId: string;
  data: Record<string, string>;
}) {
  const client_operation_id = crypto.randomUUID();
  const common = { client_operation_id };
  const { slug, fieldId, workspaceId, data } = input;

  if (slug === 'riego') {
    return apiFetch(`/api/v1/fields/${encodeURIComponent(fieldId)}/irrigations`, {
      method: 'POST', workspaceId,
      body: JSON.stringify({
        ...common,
        occurred_at: occurredAt(data.date),
        duration_hours: numberValue(data.duration),
        water_m3: numberValue(data.water),
        cost_eur: numberValue(data.cost),
        notes: data.notes || undefined,
        follow_up: followUp(data),
      }),
    });
  }

  if (slug === 'tratamiento') {
    return apiFetch(`/api/v1/fields/${encodeURIComponent(fieldId)}/treatments`, {
      method: 'POST', workspaceId,
      body: JSON.stringify({
        ...common,
        occurred_at: occurredAt(data.date),
        reason: data.reason,
        product_name: data.product,
        dose: data.dose || undefined,
        quantity: data.quantity || undefined,
        cost_eur: numberValue(data.cost),
        notes: data.notes || undefined,
        follow_up: followUp(data),
      }),
    });
  }

  if (slug === 'abono') {
    return apiFetch(`/api/v1/fields/${encodeURIComponent(fieldId)}/fertilizations`, {
      method: 'POST', workspaceId,
      body: JSON.stringify({
        ...common,
        occurred_at: occurredAt(data.date),
        product_name: data.product,
        quantity_kg: numberValue(data.quantity),
        application_method: data.method || undefined,
        cost_eur: numberValue(data.cost),
        notes: data.notes || undefined,
      }),
    });
  }

  if (slug === 'poda') {
    return apiFetch(`/api/v1/fields/${encodeURIComponent(fieldId)}/prunings`, {
      method: 'POST', workspaceId,
      body: JSON.stringify({
        ...common,
        occurred_at: occurredAt(data.date),
        pruning_type: data.type,
        workers: numberValue(data.workers),
        hours: numberValue(data.hours),
        cost_eur: numberValue(data.cost),
        notes: data.notes || undefined,
        follow_up: followUp(data),
      }),
    });
  }

  return apiFetch(`/api/v1/fields/${encodeURIComponent(fieldId)}/expenses`, {
    method: 'POST', workspaceId,
    body: JSON.stringify({
      ...common,
      occurred_on: data.date ?? new Date().toISOString().slice(0, 10),
      category: data.category,
      concept: data.concept,
      amount_eur: numberValue(data.amount),
      notes: data.notes || undefined,
    }),
  });
}
