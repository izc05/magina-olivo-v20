import { apiFetch } from '@/lib/api-client';

export type FieldDocument = {
  id: string;
  kind: string;
  title: string;
  status: string;
  created_at: string;
  domain_type: string | null;
  domain_record_id: string | null;
  relation: string;
};

export async function loadFieldDocuments(workspaceId: string, fieldId: string) {
  const response = await apiFetch<{ documents: FieldDocument[] }>(`/api/v1/fields/${encodeURIComponent(fieldId)}/documents`, { workspaceId });
  return response.documents;
}

export async function getDocumentReadUrl(workspaceId: string, documentId: string) {
  return apiFetch<{ url: string; title: string; original_filename: string; expires_in_seconds: number }>(
    `/api/v1/documents/${encodeURIComponent(documentId)}/read-url`,
    { workspaceId },
  );
}
