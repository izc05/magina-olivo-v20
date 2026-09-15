import { apiFetch } from '@/lib/api-client';

export type NotificationDeliveryStatus = 'pending' | 'dispatched' | 'suppressed' | 'failed';

export type NotificationHistoryItem = {
  id: string;
  fieldId?: string;
  fieldName?: string;
  kind: string;
  sourceType: string;
  title: string;
  body: string;
  status: NotificationDeliveryStatus;
  createdAt: string;
  dispatchedAt?: string;
  actionPath?: string;
};

export type NotificationHistory = {
  items: NotificationHistoryItem[];
  counts: {
    total: number;
    pending: number;
    dispatched: number;
    suppressed: number;
    failed: number;
  };
  semantics: 'delivery_history_not_read_state';
};

type ApiNotificationHistory = {
  items: Array<{
    id: string;
    field_id: string | null;
    field_name: string | null;
    kind: string;
    source_type: string;
    title: string;
    body: string;
    status: NotificationDeliveryStatus;
    created_at: string;
    dispatched_at: string | null;
    action_path: string | null;
  }>;
  counts: NotificationHistory['counts'];
  semantics: NotificationHistory['semantics'];
};

export async function loadNotificationHistory(workspaceId: string, limit = 100): Promise<NotificationHistory> {
  const data = await apiFetch<ApiNotificationHistory>(`/api/v1/notifications?limit=${encodeURIComponent(String(limit))}`, { workspaceId });
  return {
    items: data.items.map((item) => ({
      id: item.id,
      fieldId: item.field_id ?? undefined,
      fieldName: item.field_name ?? undefined,
      kind: item.kind,
      sourceType: item.source_type,
      title: item.title,
      body: item.body,
      status: item.status,
      createdAt: item.created_at,
      dispatchedAt: item.dispatched_at ?? undefined,
      actionPath: item.action_path ?? undefined,
    })),
    counts: data.counts,
    semantics: data.semantics,
  };
}

export function emptyNotificationHistory(): NotificationHistory {
  return {
    items: [],
    counts: { total: 0, pending: 0, dispatched: 0, suppressed: 0, failed: 0 },
    semantics: 'delivery_history_not_read_state',
  };
}
