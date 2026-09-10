import { apiFetch } from '@/lib/api-client';

export type PlannedTaskKind = 'treatment' | 'irrigation' | 'fertilization' | 'pruning' | 'harvest' | 'work' | 'observation' | 'other';

export type PlannedTask = {
  id: string;
  fieldId: string;
  fieldName: string;
  title: string;
  scheduledAt: string;
  status: 'planned' | 'completed' | 'postponed' | 'cancelled';
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
  status?: 'planned' | 'postponed' | 'cancelled';
}): Promise<PlannedTask> {
  const data = await apiFetch<ApiPlannedTask>(`/api/v1/planned-tasks/${encodeURIComponent(input.taskId)}`, {
    method: 'PATCH',
    workspaceId: input.workspaceId,
    body: JSON.stringify({ scheduled_at: input.scheduledAt, status: input.status }),
  });
  return mapTask(data);
}

export async function completePlannedTask(input: {
  workspaceId: string;
  taskId: string;
  domainType: 'treatment' | 'irrigation' | 'fertilization' | 'pruning' | 'harvest_delivery' | 'work';
  domainRecordId: string;
}): Promise<PlannedTask> {
  const data = await apiFetch<ApiPlannedTask>(`/api/v1/planned-tasks/${encodeURIComponent(input.taskId)}/complete`, {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({ domain_type: input.domainType, domain_record_id: input.domainRecordId }),
  });
  return mapTask(data);
}
