import { apiFetch } from '@/lib/api-client';

export type HomePriorityPreferences = {
  economicWeight: 'normal' | 'reduced';
  documentWeight: 'normal' | 'reduced';
  showLowPriority: boolean;
};

type ApiResponse = {
  preferences: {
    economic_weight: 'normal' | 'reduced';
    document_weight: 'normal' | 'reduced';
    show_low_priority: boolean;
  };
  protected_categories: string[];
  rule: string;
};

export const defaultHomePriorityPreferences: HomePriorityPreferences = {
  economicWeight: 'normal',
  documentWeight: 'normal',
  showLowPriority: true,
};

function map(response: ApiResponse): HomePriorityPreferences {
  return {
    economicWeight: response.preferences.economic_weight,
    documentWeight: response.preferences.document_weight,
    showLowPriority: response.preferences.show_low_priority,
  };
}

export async function loadHomePriorityPreferences(workspaceId: string) {
  const response = await apiFetch<ApiResponse>('/api/v1/home-priority/preferences', { workspaceId });
  return map(response);
}

export async function saveHomePriorityPreferences(workspaceId: string, preferences: HomePriorityPreferences) {
  const response = await apiFetch<ApiResponse>('/api/v1/home-priority/preferences', {
    method: 'PUT',
    workspaceId,
    body: JSON.stringify({
      economic_weight: preferences.economicWeight,
      document_weight: preferences.documentWeight,
      show_low_priority: preferences.showLowPriority,
    }),
  });
  return map(response);
}
