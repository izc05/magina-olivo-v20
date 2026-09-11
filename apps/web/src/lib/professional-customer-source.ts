import { apiFetch } from '@/lib/api-client';

export type ProfessionalCustomerDetail = {
  customer: { id: string; display_name: string; legal_name?: string | null; tax_id?: string | null; phone?: string | null; email?: string | null; notes?: string | null };
  summary: { work_count: number; invoice_count: number; charged_eur: number; collected_eur: number; pending_eur: number; direct_cost_eur: number; accrued_margin_eur: number };
  works: Array<{ id: string; occurred_on: string; title: string; site_name?: string | null; charge_eur: number | null; collected_eur: number; pending_eur: number; direct_cost_eur: number; accrued_margin_eur: number; payment_status: string; invoice_id?: string | null; invoice_number?: string | null }>;
  invoices: Array<{ id: string; invoice_number?: string | null; issued_on?: string | null; due_on?: string | null; status: string; subtotal_eur: number; tax_eur: number; total_eur: number; collected_eur: number; pending_eur: number; work_count: number }>;
  collections: Array<{ id: string; collected_on: string; amount_eur: number; method?: string | null; reference?: string | null; work_id: string; work_title: string }>;
};

export async function loadProfessionalCustomer(workspaceId: string, customerId: string) {
  return apiFetch<ProfessionalCustomerDetail>(`/api/v1/professional/customers/${encodeURIComponent(customerId)}`, { workspaceId });
}
