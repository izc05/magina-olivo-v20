import { apiFetch } from '@/lib/api-client';

export type PlannedTaskStatus = 'planned' | 'postponed' | 'cancelled';

export async function updatePlannedTask(
  workspaceId: string,
  taskId: string,
  patch: {
    scheduled_at?: string;
    title?: string;
    notes?: string | null;
    status?: PlannedTaskStatus;
  },
) {
  return apiFetch(`/api/v1/planned-tasks/${encodeURIComponent(taskId)}`, {
    method: 'PATCH',
    workspaceId,
    body: JSON.stringify(patch),
  });
}

export function localDateTimeValue(iso: string) {
  const date = new Date(iso);
  if (!Number.isFinite(date.getTime())) return '';
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function dateTimeLocalToIso(value: string) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) throw new Error('invalid_datetime');
  return date.toISOString();
}

export function postponeIso(currentIso: string, days = 1) {
  const date = new Date(currentIso);
  if (!Number.isFinite(date.getTime())) throw new Error('invalid_datetime');
  date.setDate(date.getDate() + days);
  return date.toISOString();
}
