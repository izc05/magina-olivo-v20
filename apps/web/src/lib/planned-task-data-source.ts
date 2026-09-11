import { apiFetch } from '@/lib/api-client';

export type PlannedTaskKind = 'treatment' | 'irrigation' | 'fertilization' | 'pruning' | 'harvest' | 'work' | 'observation' | 'other';
export type PlannedTaskStatus = 'planned' | 'completed' | 'postponed' | 'cancelled';
export type PlannedTaskListStatus = PlannedTaskStatus | 'active' | 'all';

export type PlannedTask = {
  id: string;
  fieldId: string;
  fieldName: string;
  title: string;
  scheduledAt: string;
  status: PlannedTaskStatus;
  taskKind?: PlannedTaskKind;
  notes?: string;
  completion?: {
    domainType?: string;
    domainRecordId: string;
    completedAt?: string;
  };
};

type ApiPlannedTask = {
  id: string;
  field_id: string;
  field_name: string;
  title: string;
  scheduled_at: string;
  status: PlannedTask['status'];
  task_kind: PlannedTaskKind | null;
  notes: string | null;
  completion: null | {
    domain_type: string | null;
    domain_record_id: string;
    completed_at: string | null;
  };
};

function mapTask(data: ApiPlannedTask): PlannedTask {
  return {
    id: data.id,
    fieldId: data.field_id,
    fieldName: data.field_name,
    title: data.title,
    scheduledAt: data.scheduled_at,
    status: data.status,
    taskKind: data.task_kind ?? undefined,
    notes: data.notes ?? undefined,
    completion: data.completion ? {
      domainType: data.completion.domain_type ?? undefined,
      domainRecordId: data.completion.domain_record_id,
      completedAt: data.completion.completed_at ?? undefined,
    } : undefined,
  };
}

export async function listPlannedTasks(input: {
  workspaceId: string;
  status?: PlannedTaskListStatus;
  fieldId?: string;
  from?: string;
  to?: string;
}): Promise<PlannedTask[]> {
  const params = new URLSearchParams();
  if (input.status && input.status !== 'all') params.set('status', input.status);
  if (input.fieldId) params.set('field_id', input.fieldId);
  if (input.from) params.set('from', input.from);
  if (input.to) params.set('to', input.to);
  const suffix = params.size ? `?${params.toString()}` : '';
  const data = await apiFetch<{ tasks: ApiPlannedTask[] }>(`/api/v1/planned-tasks${suffix}`, {
    workspaceId: input.workspaceId,
  });
  return data.tasks.map(mapTask);
}

export async function createPlannedTask(input: {
  workspaceId: string;
  fieldId: string;
  title: string;
  scheduledAt: string;
  taskKind: PlannedTaskKind;
  notes?: string;
}): Promise<PlannedTask> {
  const data = await apiFetch<ApiPlannedTask>(`/api/v1/fields/${encodeURIComponent(input.fieldId)}/planned-tasks`, {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({
      title: input.title,
      scheduled_at: input.scheduledAt,
      task_kind: input.taskKind,
      notes: input.notes || null,
    }),
  });
  return mapTask(data);
}

export async function updatePlannedTask(input: {
  workspaceId: string;
  taskId: string;
  scheduledAt?: string;
  title?: string;
  notes?: string | null;
  status?: 'planned' | 'postponed' | 'cancelled';
}): Promise<PlannedTask> {
  const body: Record<string, string | null> = {};
  if (input.scheduledAt !== undefined) body.scheduled_at = input.scheduledAt;
  if (input.title !== undefined) body.title = input.title;
  if (input.notes !== undefined) body.notes = input.notes;
  if (input.status !== undefined) body.status = input.status;

  const data = await apiFetch<ApiPlannedTask>(`/api/v1/planned-tasks/${encodeURIComponent(input.taskId)}`, {
    method: 'PATCH',
    workspaceId: input.workspaceId,
    body: JSON.stringify(body),
  });
  return mapTask(data);
}

export async function completePlannedTask(input: {
  workspaceId: string;
  taskId: string;
  domainType: 'treatment' | 'irrigation' | 'fertilization' | 'pruning' | 'observation' | 'harvest_delivery' | 'work';
  domainRecordId: string;
}): Promise<PlannedTask> {
  const data = await apiFetch<ApiPlannedTask>(`/api/v1/planned-tasks/${encodeURIComponent(input.taskId)}/complete`, {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({ domain_type: input.domainType, domain_record_id: input.domainRecordId }),
  });
  return mapTask(data);
}

export function localDateTimeValue(iso?: string) {
  const date = iso ? new Date(iso) : new Date();
  if (Number.isNaN(date.getTime())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

export function dateTimeLocalToIso(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error('invalid_datetime');
  return date.toISOString();
}

export function postponeIso(currentIso: string, days = 1) {
  const date = new Date(currentIso);
  if (Number.isNaN(date.getTime())) throw new Error('invalid_datetime');
  date.setDate(date.getDate() + days);
  return date.toISOString();
}

export function plannedTaskExecutionHref(task: Pick<PlannedTask, 'id' | 'fieldId' | 'taskKind'>) {
  const routeByKind: Record<PlannedTaskKind, string> = {
    treatment: 'tratamiento',
    irrigation: 'riego',
    fertilization: 'abono',
    pruning: 'poda',
    harvest: 'cosecha',
    work: 'trabajo',
    observation: 'observacion',
    other: 'trabajo',
  };
  const slug = routeByKind[task.taskKind ?? 'other'];
  const params = new URLSearchParams({ fieldId: task.fieldId, source: 'api', plannedEventId: task.id });
  return `/mi-campo/registrar/${slug}?${params.toString()}`;
}
