import { apiFetch } from '@/lib/api-client';

export type SupportedApiRecordSlug = 'riego' | 'tratamiento' | 'abono' | 'poda' | 'gasto' | 'jornal' | 'maquinaria' | 'observacion';
export type SavedApiRecord = {
  recordId: string;
  domainType: 'irrigation' | 'treatment' | 'fertilization' | 'pruning' | 'observation' | 'expense' | 'work';
};

type RecordEnvelope = Record<string, { id?: string } | boolean | undefined>;

export function supportsApiRecord(slug: string): slug is SupportedApiRecordSlug {
  return ['riego', 'tratamiento', 'abono', 'poda', 'gasto', 'jornal', 'maquinaria', 'observacion'].includes(slug);
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

function identity(data: RecordEnvelope, key: string, domainType: SavedApiRecord['domainType']): SavedApiRecord {
  const value = data[key];
  const id = typeof value === 'object' && value ? value.id : undefined;
  if (!id) throw new Error(`API response did not include ${key}.id`);
  return { recordId: id, domainType };
}

export async function saveApiRecord(input: {
  slug: SupportedApiRecordSlug;
  fieldId: string;
  workspaceId: string;
  data: Record<string, string>;
}): Promise<SavedApiRecord> {
  const client_operation_id = crypto.randomUUID();
  const common = { client_operation_id };
  const { slug, fieldId, workspaceId, data } = input;

  if (slug === 'riego') {
    const response = await apiFetch<RecordEnvelope>(`/api/v1/fields/${encodeURIComponent(fieldId)}/irrigations`, {
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
    return identity(response, 'irrigation', 'irrigation');
  }

  if (slug === 'tratamiento') {
    const response = await apiFetch<RecordEnvelope>(`/api/v1/fields/${encodeURIComponent(fieldId)}/treatments`, {
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
    return identity(response, 'treatment', 'treatment');
  }

  if (slug === 'abono') {
    const response = await apiFetch<RecordEnvelope>(`/api/v1/fields/${encodeURIComponent(fieldId)}/fertilizations`, {
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
    return identity(response, 'fertilization', 'fertilization');
  }

  if (slug === 'poda') {
    const response = await apiFetch<RecordEnvelope>(`/api/v1/fields/${encodeURIComponent(fieldId)}/prunings`, {
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
    return identity(response, 'pruning', 'pruning');
  }

  if (slug === 'observacion') {
    const severityMap: Record<string, 'low' | 'medium' | 'high'> = {
      Baja: 'low', Media: 'medium', Alta: 'high',
    };
    const response = await apiFetch<RecordEnvelope>(`/api/v1/fields/${encodeURIComponent(fieldId)}/observations`, {
      method: 'POST', workspaceId,
      body: JSON.stringify({
        ...common,
        occurred_at: occurredAt(data.date),
        observation_type: data.type || 'Otro',
        notes: data.notes,
        severity: data.severity ? severityMap[data.severity] : undefined,
        follow_up: followUp(data),
      }),
    });
    return identity(response, 'observation', 'observation');
  }

  if (slug === 'jornal') {
    const workers = numberValue(data.workers);
    const hours = numberValue(data.hours);
    const cost = numberValue(data.cost);
    const response = await apiFetch<RecordEnvelope>('/api/v1/works', {
      method: 'POST', workspaceId,
      body: JSON.stringify({
        ...common,
        field_id: fieldId,
        type: 'manual-work',
        occurred_on: data.date ?? new Date().toISOString().slice(0, 10),
        title: data.task || 'Trabajo manual',
        notes: data.notes || undefined,
        performed_for: 'self',
        participants: [{
          display_name: data.crew || (workers ? `${workers} personas` : 'Mano de obra'),
          role: workers ? `${workers} persona${workers === 1 ? '' : 's'}` : undefined,
          quantity: hours,
          unit: hours ? 'hours' : 'fixed',
          cost_eur: cost,
        }],
        resources: [],
      }),
    });
    return identity(response, 'work', 'work');
  }

  if (slug === 'maquinaria') {
    const hours = numberValue(data.hours);
    const cost = numberValue(data.cost);
    const fuel = numberValue(data.fuel);
    const response = await apiFetch<RecordEnvelope>('/api/v1/works', {
      method: 'POST', workspaceId,
      body: JSON.stringify({
        ...common,
        field_id: fieldId,
        type: 'machinery-work',
        occurred_on: data.date ?? new Date().toISOString().slice(0, 10),
        title: data.task || `Trabajo con ${data.machine || 'maquinaria'}`,
        notes: [data.notes, fuel !== undefined ? `Combustible: ${fuel} L` : undefined].filter(Boolean).join(' · ') || undefined,
        performed_for: 'self',
        participants: [],
        resources: [{
          kind: 'machinery',
          name: data.machine || 'Maquinaria',
          quantity: hours,
          unit: hours ? 'h' : undefined,
          cost_eur: cost,
        }],
      }),
    });
    return identity(response, 'work', 'work');
  }

  const response = await apiFetch<RecordEnvelope>(`/api/v1/fields/${encodeURIComponent(fieldId)}/expenses`, {
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
  return identity(response, 'expense', 'expense');
}
