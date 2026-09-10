import { apiFetch } from '@/lib/api-client';

export type AgendaItem = {
  id: string;
  fieldId?: string;
  fieldName?: string;
  title: string;
  scheduledAt: string;
  bucket: 'overdue' | 'today' | 'upcoming';
  priority: 'high' | 'normal' | 'low';
  sourceDomainType?: string;
  taskKind?: string;
  notes?: string;
  status?: 'planned' | 'postponed';
  weatherSensitive: boolean;
  weatherContextUrl?: string;
};

export type AgendaView = {
  date: string;
  overdue: AgendaItem[];
  today: AgendaItem[];
  upcoming: AgendaItem[];
  counts: { overdue: number; today: number; upcoming: number; weatherSensitive: number };
  rule: string;
};

type ApiAgendaItem = {
  id: string;
  field_id: string | null;
  field_name: string | null;
  source_domain_type: string | null;
  task_kind: string | null;
  notes: string | null;
  title: string;
  scheduled_at: string;
  status: 'planned' | 'postponed';
  bucket: 'overdue' | 'today' | 'upcoming';
  priority: 'high' | 'normal' | 'low';
  weather_sensitive: boolean;
  weather_context_url: string | null;
};

type ApiAgenda = {
  date: string;
  overdue: ApiAgendaItem[];
  today: ApiAgendaItem[];
  upcoming: ApiAgendaItem[];
  counts: AgendaView['counts'];
  rule: string;
};

function mapItem(item: ApiAgendaItem): AgendaItem {
  return {
    id: item.id,
    fieldId: item.field_id ?? undefined,
    fieldName: item.field_name ?? undefined,
    title: item.title,
    scheduledAt: item.scheduled_at,
    bucket: item.bucket,
    priority: item.priority,
    sourceDomainType: item.source_domain_type ?? undefined,
    taskKind: item.task_kind ?? undefined,
    notes: item.notes ?? undefined,
    status: item.status,
    weatherSensitive: item.weather_sensitive,
    weatherContextUrl: item.weather_context_url ?? undefined,
  };
}

export async function loadApiAgenda(workspaceId: string, date?: string): Promise<AgendaView> {
  const suffix = date ? `?date=${encodeURIComponent(date)}` : '';
  const data = await apiFetch<ApiAgenda>(`/api/v1/agenda/today${suffix}`, { workspaceId });
  return {
    date: data.date,
    overdue: data.overdue.map(mapItem),
    today: data.today.map(mapItem),
    upcoming: data.upcoming.map(mapItem),
    counts: data.counts,
    rule: data.rule,
  };
}

export function emptyAgenda(date = new Date().toISOString().slice(0, 10)): AgendaView {
  return { date, overdue: [], today: [], upcoming: [], counts: { overdue: 0, today: 0, upcoming: 0, weatherSensitive: 0 }, rule: 'El clima puede advertir o priorizar una tarea, pero nunca completarla o cancelarla automáticamente.' };
}
