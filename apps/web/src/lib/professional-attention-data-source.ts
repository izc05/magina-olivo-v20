import { apiFetch } from '@/lib/api-client';

export type ProfessionalAttentionSummary = {
  summary: {
    overdueInvoiceCount: number;
    overdueInvoiceEur: number;
    unbilledWorkCount: number;
    unbilledWorkEur: number;
    agedCustomerCount: number;
    agedReceivableEur: number;
    expiredQuoteCount: number;
    expiredQuoteEur: number;
    quoteFollowupCount: number;
    quoteFollowupEur: number;
  };
  overdueInvoices: Array<{
    id: string;
    invoiceNumber?: string;
    customerId: string;
    customerName: string;
    dueOn: string;
    totalEur: number;
    collectedEur: number;
    pendingEur: number;
    overdueDays: number;
  }>;
  unbilledWorks: Array<{
    id: string;
    occurredOn: string;
    title: string;
    customerId: string;
    customerName: string;
    chargeEur: number;
    ageDays: number;
  }>;
  agedCustomers: Array<{
    customerId: string;
    customerName: string;
    pendingEur: number;
    oldestUnpaidOn: string;
    ageDays: number;
    workCount: number;
  }>;
  expiredQuotes: Array<{
    id: string;
    quoteNumber?: string;
    title: string;
    customerId: string;
    customerName: string;
    validUntil: string;
    totalEur: number;
    overdueDays: number;
  }>;
  quoteFollowups: Array<{
    id: string;
    quoteNumber?: string;
    title: string;
    customerId: string;
    customerName: string;
    issuedOn?: string;
    validUntil?: string;
    totalEur: number;
    ageDays: number;
  }>;
};

type ApiPayload = {
  summary: {
    overdue_invoice_count: number;
    overdue_invoice_eur: number;
    unbilled_work_count: number;
    unbilled_work_eur: number;
    aged_customer_count: number;
    aged_receivable_eur: number;
    expired_quote_count: number;
    expired_quote_eur: number;
    quote_followup_count: number;
    quote_followup_eur: number;
  };
  overdue_invoices: Array<{
    id: string;
    invoice_number: string | null;
    customer_id: string;
    customer_name: string;
    due_on: string;
    total_eur: number;
    collected_eur: number;
    pending_eur: number;
    overdue_days: number;
  }>;
  unbilled_works: Array<{
    id: string;
    occurred_on: string;
    title: string;
    customer_id: string;
    customer_name: string;
    charge_eur: number;
    age_days: number;
  }>;
  aged_customers: Array<{
    customer_id: string;
    customer_name: string;
    pending_eur: number;
    oldest_unpaid_on: string;
    age_days: number;
    work_count: number;
  }>;
  expired_quotes: Array<{
    id: string;
    quote_number: string | null;
    title: string;
    customer_id: string;
    customer_name: string;
    valid_until: string;
    total_eur: number;
    overdue_days: number;
  }>;
  quote_followups: Array<{
    id: string;
    quote_number: string | null;
    title: string;
    customer_id: string;
    customer_name: string;
    issued_on: string | null;
    valid_until: string | null;
    total_eur: number;
    age_days: number;
  }>;
};

export async function loadProfessionalAttention(workspaceId: string, limit = 8): Promise<ProfessionalAttentionSummary> {
  const payload = await apiFetch<ApiPayload>(`/api/v1/professional/attention?limit=${limit}`, { workspaceId });
  return {
    summary: {
      overdueInvoiceCount: payload.summary.overdue_invoice_count,
      overdueInvoiceEur: payload.summary.overdue_invoice_eur,
      unbilledWorkCount: payload.summary.unbilled_work_count,
      unbilledWorkEur: payload.summary.unbilled_work_eur,
      agedCustomerCount: payload.summary.aged_customer_count,
      agedReceivableEur: payload.summary.aged_receivable_eur,
      expiredQuoteCount: payload.summary.expired_quote_count,
      expiredQuoteEur: payload.summary.expired_quote_eur,
      quoteFollowupCount: payload.summary.quote_followup_count,
      quoteFollowupEur: payload.summary.quote_followup_eur,
    },
    overdueInvoices: payload.overdue_invoices.map((item) => ({
      id: item.id,
      invoiceNumber: item.invoice_number ?? undefined,
      customerId: item.customer_id,
      customerName: item.customer_name,
      dueOn: item.due_on,
      totalEur: item.total_eur,
      collectedEur: item.collected_eur,
      pendingEur: item.pending_eur,
      overdueDays: item.overdue_days,
    })),
    unbilledWorks: payload.unbilled_works.map((item) => ({
      id: item.id,
      occurredOn: item.occurred_on,
      title: item.title,
      customerId: item.customer_id,
      customerName: item.customer_name,
      chargeEur: item.charge_eur,
      ageDays: item.age_days,
    })),
    agedCustomers: payload.aged_customers.map((item) => ({
      customerId: item.customer_id,
      customerName: item.customer_name,
      pendingEur: item.pending_eur,
      oldestUnpaidOn: item.oldest_unpaid_on,
      ageDays: item.age_days,
      workCount: item.work_count,
    })),
    expiredQuotes: payload.expired_quotes.map((item) => ({
      id: item.id,
      quoteNumber: item.quote_number ?? undefined,
      title: item.title,
      customerId: item.customer_id,
      customerName: item.customer_name,
      validUntil: item.valid_until,
      totalEur: item.total_eur,
      overdueDays: item.overdue_days,
    })),
    quoteFollowups: payload.quote_followups.map((item) => ({
      id: item.id,
      quoteNumber: item.quote_number ?? undefined,
      title: item.title,
      customerId: item.customer_id,
      customerName: item.customer_name,
      issuedOn: item.issued_on ?? undefined,
      validUntil: item.valid_until ?? undefined,
      totalEur: item.total_eur,
      ageDays: item.age_days,
    })),
  };
}
