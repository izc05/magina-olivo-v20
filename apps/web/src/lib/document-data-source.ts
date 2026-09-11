import { apiFetch } from '@/lib/api-client';
import type { DocumentKind } from '@/lib/document-upload-source';

export type FieldDocument = {
  id: string;
  kind: string;
  title: string;
  status: string;
  created_at: string;
  domain_type: string | null;
  domain_record_id: string | null;
  relation: string;
  campaign_id?: string | null;
  campaign_name?: string | null;
};

export async function loadFieldDocuments(workspaceId: string, fieldId: string, filters?: {
  kind?: DocumentKind;
  campaignId?: string;
  unassigned?: boolean;
}) {
  const params = new URLSearchParams();
  if (filters?.kind) params.set('kind', filters.kind);
  if (filters?.campaignId) params.set('campaignId', filters.campaignId);
  if (filters?.unassigned) params.set('unassigned', 'true');
  const suffix = params.toString() ? `?${params.toString()}` : '';
  const response = await apiFetch<{ documents: FieldDocument[] }>(`/api/v1/fields/${encodeURIComponent(fieldId)}/document-catalog${suffix}`, { workspaceId });
  return response.documents;
}

export async function assignDocumentCampaign(workspaceId: string, documentId: string, campaignId?: string) {
  return apiFetch(`/api/v1/documents/${encodeURIComponent(documentId)}/campaign`, {
    method: 'PATCH',
    workspaceId,
    body: JSON.stringify({ campaign_id: campaignId || null }),
  });
}

export async function getDocumentReadUrl(workspaceId: string, documentId: string) {
  return apiFetch<{ url: string; title: string; original_filename: string; expires_in_seconds: number }>(
    `/api/v1/documents/${encodeURIComponent(documentId)}/read-url`,
    { workspaceId },
  );
}
