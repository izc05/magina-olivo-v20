import { apiFetch } from '@/lib/api-client';

export type CommercialNotificationPreferences = {
  enabled: boolean;
  notify_overdue_invoices: boolean;
  overdue_invoice_days: number;
  notify_expired_quotes: boolean;
  notify_quote_followup: boolean;
  quote_followup_days: number;
  notify_unbilled_work: boolean;
  unbilled_work_days: number;
};

export function loadCommercialNotificationPreferences(workspaceId: string) {
  return apiFetch<CommercialNotificationPreferences>('/api/v1/commercial-notifications/preferences', { workspaceId });
}

export function saveCommercialNotificationPreferences(workspaceId: string, input: CommercialNotificationPreferences) {
  return apiFetch<CommercialNotificationPreferences>('/api/v1/commercial-notifications/preferences', {
    method: 'PUT', workspaceId, body: JSON.stringify(input),
  });
}
