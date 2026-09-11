import { apiFetch } from '@/lib/api-client';

export type ProfessionalBusinessProfile = {
  workspace_id: string;
  workspace_name: string;
  legal_name?: string | null;
  tax_id?: string | null;
  address?: string | null;
  postal_code?: string | null;
  municipality?: string | null;
  province?: string | null;
  email?: string | null;
  phone?: string | null;
  payment_terms?: string | null;
  footer_note?: string | null;
};

export async function loadProfessionalBusinessProfile(workspaceId: string) {
  const response = await apiFetch<{ profile: ProfessionalBusinessProfile }>('/api/v1/professional/business-profile', { workspaceId });
  return response.profile;
}

export async function saveProfessionalBusinessProfile(workspaceId: string, profile: Omit<ProfessionalBusinessProfile, 'workspace_id' | 'workspace_name'>) {
  const response = await apiFetch<{ profile: ProfessionalBusinessProfile }>('/api/v1/professional/business-profile', {
    method: 'PUT',
    workspaceId,
    body: JSON.stringify(profile),
  });
  return response.profile;
}
