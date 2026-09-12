import { apiFetch } from '@/lib/api-client';

export type FinancialNotificationPreferences = {
  enabled: boolean;
  notifySettlements: boolean;
  settlementMinEur: number;
  notifyDocumentReview: boolean;
  notifyOcrFailure: boolean;
};

type ApiPreferences = {
  enabled: boolean;
  notify_settlements: boolean;
  settlement_min_eur: number;
  notify_document_review: boolean;
  notify_ocr_failure: boolean;
};

function map(input: ApiPreferences): FinancialNotificationPreferences {
  return {
    enabled: input.enabled,
    notifySettlements: input.notify_settlements,
    settlementMinEur: Number(input.settlement_min_eur),
    notifyDocumentReview: input.notify_document_review,
    notifyOcrFailure: input.notify_ocr_failure,
  };
}

export async function loadFinancialNotificationPreferences(workspaceId: string) {
  return map(await apiFetch<ApiPreferences>('/api/v1/financial-notifications/preferences', { workspaceId }));
}

export async function saveFinancialNotificationPreferences(workspaceId: string, preferences: FinancialNotificationPreferences) {
  return map(await apiFetch<ApiPreferences>('/api/v1/financial-notifications/preferences', {
    method: 'PUT',
    workspaceId,
    body: JSON.stringify({
      enabled: preferences.enabled,
      notify_settlements: preferences.notifySettlements,
      settlement_min_eur: preferences.settlementMinEur,
      notify_document_review: preferences.notifyDocumentReview,
      notify_ocr_failure: preferences.notifyOcrFailure,
    }),
  }));
}

export async function evaluateFinancialNotifications(workspaceId: string) {
  return apiFetch('/api/v1/financial-notifications/evaluate', { method: 'POST', workspaceId });
}
