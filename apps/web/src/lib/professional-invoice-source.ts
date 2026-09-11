import { apiFetch } from '@/lib/api-client';

export type InvoiceCandidateWork = {
  id: string;
  occurred_on: string;
  title: string;
  charge_eur: number | string | null;
  quoted_amount_eur: number | string | null;
  invoice_reference: string | null;
  site_name: string | null;
  collected_eur: number | string;
};

export type ProfessionalInvoiceView = {
  id: string;
  customer_party_id: string;
  customer_name: string;
  invoice_number: string | null;
  issued_on: string | null;
  due_on: string | null;
  status: 'draft' | 'issued' | 'void';
  subtotal_eur: number | string;
  tax_eur: number | string;
  total_eur: number | string;
  work_count: number;
  collected_eur: number;
  pending_eur: number;
};

export async function loadInvoiceCandidates(workspaceId: string, customerId: string) {
  const response = await apiFetch<{ works: InvoiceCandidateWork[] }>(
    `/api/v1/professional/invoice-candidates?customerId=${encodeURIComponent(customerId)}`,
    { workspaceId },
  );
  return response.works;
}

export async function loadProfessionalInvoices(workspaceId: string, customerId?: string) {
  const suffix = customerId ? `?customerId=${encodeURIComponent(customerId)}` : '';
  const response = await apiFetch<{ invoices: ProfessionalInvoiceView[] }>(`/api/v1/professional/invoices${suffix}`, { workspaceId });
  return response.invoices;
}

export async function createProfessionalInvoice(workspaceId: string, payload: Record<string, unknown>) {
  const response = await apiFetch<{ invoice: { id: string } }>('/api/v1/professional/invoices', {
    method: 'POST',
    workspaceId,
    body: JSON.stringify({ client_operation_id: crypto.randomUUID(), ...payload }),
  });
  return response.invoice;
}
