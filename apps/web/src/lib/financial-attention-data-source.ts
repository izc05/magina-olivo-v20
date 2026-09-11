import { apiFetch } from '@/lib/api-client';

export type FinancialAttentionSummary = {
  generatedAt: string;
  fieldId?: string;
  counts: { pendingSettlements: number; pendingDocuments: number };
  pendingCollectionEur: number;
  settlements: Array<{
    id: string;
    title: string;
    subtitle: string;
    pendingEur: number;
    netEur: number;
    collectedEur: number;
    date: string;
  }>;
  documents: Array<{
    id: string;
    title: string;
    subtitle: string;
    fieldId: string;
    kind: string;
    status: 'needs_ocr' | 'processing' | 'ocr_failed' | 'needs_review' | 'unstructured';
    date: string;
  }>;
};

type ApiResponse = {
  generated_at: string;
  field_id: string | null;
  counts: { pending_settlements: number; pending_documents: number };
  pending_collection_eur: number;
  settlements: Array<{
    id: string;
    title: string;
    subtitle: string;
    pending_eur: number;
    net_eur: number;
    collected_eur: number;
    date: string;
  }>;
  documents: Array<{
    id: string;
    title: string;
    subtitle: string;
    field_id: string;
    kind: string;
    status: FinancialAttentionSummary['documents'][number]['status'];
    date: string;
  }>;
};

export async function loadFinancialAttention(input: { workspaceId: string; fieldId?: string; limit?: number }): Promise<FinancialAttentionSummary> {
  const query = new URLSearchParams();
  if (input.fieldId) query.set('fieldId', input.fieldId);
  if (input.limit) query.set('limit', String(input.limit));
  const response = await apiFetch<ApiResponse>(`/api/v1/financial-attention?${query.toString()}`, { workspaceId: input.workspaceId });
  return {
    generatedAt: response.generated_at,
    fieldId: response.field_id ?? undefined,
    counts: { pendingSettlements: response.counts.pending_settlements, pendingDocuments: response.counts.pending_documents },
    pendingCollectionEur: response.pending_collection_eur,
    settlements: response.settlements.map((item) => ({
      id: item.id,
      title: item.title,
      subtitle: item.subtitle,
      pendingEur: item.pending_eur,
      netEur: item.net_eur,
      collectedEur: item.collected_eur,
      date: item.date,
    })),
    documents: response.documents.map((item) => ({
      id: item.id,
      title: item.title,
      subtitle: item.subtitle,
      fieldId: item.field_id,
      kind: item.kind,
      status: item.status,
      date: item.date,
    })),
  };
}
