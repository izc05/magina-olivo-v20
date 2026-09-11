import { apiFetch } from '@/lib/api-client';

export type ProfessionalCustomerDetail = {
  customer: { id: string; display_name: string; legal_name?: string | null; tax_id?: string | null; phone?: string | null; email?: string | null; notes?: string | null };
  summary: { work_count: number; invoice_count: number; charged_eur: number; collected_eur: number; pending_eur: number; direct_cost_eur: number; accrued_margin_eur: number };
  quote_summary: {
    quote_count: number;
    accepted_quote_count: number;
    rejected_quote_count: number;
    conversion_count: number;
    acceptance_rate_percent: number | null;
    accepted_quoted_eur: number;
    converted_quoted_eur: number;
    converted_real_cost_eur: number;
    converted_invoiced_eur: number;
    converted_invoiced_less_real_cost_eur: number;
  };
  works: Array<{ id: string; occurred_on: string; title: string; site_name?: string | null; charge_eur: number | null; collected_eur: number; pending_eur: number; direct_cost_eur: number; accrued_margin_eur: number; payment_status: string; professional_quote_id?: string | null; invoice_id?: string | null; invoice_number?: string | null }>;
  invoices: Array<{ id: string; invoice_number?: string | null; issued_on?: string | null; due_on?: string | null; status: string; subtotal_eur: number; tax_eur: number; total_eur: number; collected_eur: number; pending_eur: number; work_count: number }>;
  quotes: Array<{
    id: string;
    quote_number?: string | null;
    title: string;
    issued_on?: string | null;
    valid_until?: string | null;
    status: string;
    subtotal_eur: number;
    tax_eur: number;
    total_eur: number;
    site_name?: string | null;
    work_id?: string | null;
    work_title?: string | null;
    work_charge_eur?: number | null;
    real_cost_eur: number;
    invoice_id?: string | null;
    invoice_number?: string | null;
    invoiced_eur: number;
    invoiced_less_real_cost_eur: number;
  }>;
  collections: Array<{ id: string; collected_on: string; amount_eur: number; method?: string | null; reference?: string | null; work_id: string; work_title: string }>;
  documents: Array<{ id: string; kind: string; title: string; created_at: string; domain_type: 'professional_invoice' | 'professional_quote'; domain_record_id: string; relation: string }>;
};

export async function loadProfessionalCustomer(workspaceId: string, customerId: string) {
  return apiFetch<ProfessionalCustomerDetail>(`/api/v1/professional/customers/${encodeURIComponent(customerId)}`, { workspaceId });
}
