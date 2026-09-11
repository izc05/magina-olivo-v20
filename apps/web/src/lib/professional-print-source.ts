import { apiFetch } from '@/lib/api-client';

export type ProfessionalPrintPayload = {
  document_type: 'invoice' | 'quote';
  issuer: {
    workspace_id: string;
    workspace_name: string;
    legal_name: string;
    tax_id?: string | null;
    address?: string | null;
    postal_code?: string | null;
    municipality?: string | null;
    province?: string | null;
    email?: string | null;
    phone?: string | null;
    payment_terms?: string | null;
    footer_note?: string | null;
  } | null;
  customer: {
    id: string;
    display_name: string;
    legal_name?: string | null;
    tax_id?: string | null;
    phone?: string | null;
    email?: string | null;
  };
  document: {
    id: string;
    number?: string | null;
    title?: string | null;
    issued_on?: string | null;
    due_on?: string | null;
    valid_until?: string | null;
    status: string;
    subtotal_eur: number;
    tax_eur: number;
    total_eur: number;
    notes?: string | null;
    site_name?: string | null;
  };
  lines: Array<Record<string, unknown>>;
};

export async function loadProfessionalPrintData(workspaceId: string, type: 'invoice' | 'quote', id: string) {
  return apiFetch<ProfessionalPrintPayload>(`/api/v1/professional/print/${type}/${encodeURIComponent(id)}`, { workspaceId });
}
