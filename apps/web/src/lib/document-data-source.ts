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

export type DocumentAnalysis = {
  document: { id: string; kind: string; title: string; status: string };
  version: null | {
    id: string;
    version_no: number;
    original_filename: string;
    mime_type: string;
    upload_status: 'reserved' | 'uploaded' | 'failed';
    integrity_status: 'pending' | 'verified' | 'unverified' | 'failed';
  };
  ocr: null | {
    id: string;
    provider: string;
    provider_version: string | null;
    status: 'queued' | 'processing' | 'succeeded' | 'failed';
    confidence: number | null;
    error_code: string | null;
    error_message: string | null;
    created_at: string;
    completed_at: string | null;
  };
  extraction: null | {
    id: string;
    document_type: string;
    schema_version: number;
    status: 'queued' | 'processing' | 'succeeded' | 'failed' | 'needs_review';
    data_json: Record<string, unknown>;
    confidence_json: Record<string, number>;
    created_at: string;
    completed_at: string | null;
  };
  review: null | {
    id: string;
    confirmed_fields: Record<string, unknown>;
    corrections: Record<string, unknown>;
    reviewed_by: string;
    created_at: string;
  };
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

export async function linkDocumentToDomain(input: {
  workspaceId: string;
  documentId: string;
  fieldId: string;
  domainType: 'expense' | 'harvest_delivery' | 'harvest_result';
  domainRecordId: string;
}) {
  return apiFetch(`/api/v1/documents/${encodeURIComponent(input.documentId)}/link-domain`, {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({
      field_id: input.fieldId,
      domain_type: input.domainType,
      domain_record_id: input.domainRecordId,
    }),
  });
}

export async function getDocumentReadUrl(workspaceId: string, documentId: string) {
  return apiFetch<{ url: string; title: string; original_filename: string; expires_in_seconds: number }>(
    `/api/v1/documents/${encodeURIComponent(documentId)}/read-url`,
    { workspaceId },
  );
}

export async function loadDocumentAnalysis(workspaceId: string, documentId: string) {
  return apiFetch<DocumentAnalysis>(`/api/v1/documents/${encodeURIComponent(documentId)}/analysis`, { workspaceId });
}

export async function requestDocumentOcr(workspaceId: string, documentId: string, documentVersionId: string) {
  return apiFetch<{ replayed: boolean; ocr_run_id: string; job_id: string | null; status: string }>(
    `/api/v1/documents/${encodeURIComponent(documentId)}/ocr`,
    {
      method: 'POST',
      workspaceId,
      body: JSON.stringify({
        client_operation_id: crypto.randomUUID(),
        document_version_id: documentVersionId,
        preferred_provider: 'auto',
      }),
    },
  );
}

export async function confirmDocumentExtraction(
  workspaceId: string,
  extractionId: string,
  confirmedFields: Record<string, unknown>,
  corrections: Record<string, unknown>,
) {
  return apiFetch(`/api/v1/extractions/${encodeURIComponent(extractionId)}/reviews`, {
    method: 'POST',
    workspaceId,
    body: JSON.stringify({
      extraction_run_id: extractionId,
      confirmed_fields: confirmedFields,
      corrections,
    }),
  });
}
