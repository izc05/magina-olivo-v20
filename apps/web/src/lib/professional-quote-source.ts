import { apiFetch } from '@/lib/api-client';

export type ProfessionalQuote = {
  id: string;
  customer_party_id: string;
  customer_name: string;
  customer_site_id?: string | null;
  site_name?: string | null;
  quote_number?: string | null;
  title: string;
  issued_on?: string | null;
  valid_until?: string | null;
  status: 'draft' | 'sent' | 'accepted' | 'rejected' | 'expired' | 'converted';
  subtotal_eur: number | string;
  tax_eur: number | string;
  total_eur: number | string;
  work_id?: string | null;
  work_title?: string | null;
  work_charge_eur?: number | string | null;
  actual_cost_eur?: number | string | null;
  invoice_id?: string | null;
  invoice_number?: string | null;
  invoice_total_eur?: number | string | null;
};

type QuoteMutation = Pick<ProfessionalQuote, 'id' | 'customer_party_id' | 'customer_site_id' | 'quote_number' | 'title' | 'status' | 'total_eur'>;
type ConvertedWork = { id: string; customer_party_id?: string | null; customer_site_id?: string | null; title?: string | null };

export async function loadProfessionalQuotes(workspaceId: string, customerId?: string) {
  const suffix = customerId ? `?customerId=${encodeURIComponent(customerId)}` : '';
  const response = await apiFetch<{ quotes: ProfessionalQuote[] }>(`/api/v1/professional/quotes${suffix}`, { workspaceId });
  return response.quotes;
}

export async function createProfessionalQuote(workspaceId: string, input: {
  customer_party_id: string;
  customer_site_id?: string;
  quote_number?: string;
  title: string;
  issued_on?: string;
  valid_until?: string;
  status: 'draft' | 'sent';
  subtotal_eur: number;
  tax_eur: number;
  total_eur: number;
  notes?: string;
  lines: Array<{ description: string; quantity: number; unit?: string; unit_price_eur: number; line_total_eur: number }>;
}) {
  const response = await apiFetch<{ quote: QuoteMutation }>('/api/v1/professional/quotes', {
    method: 'POST', workspaceId,
    body: JSON.stringify({ client_operation_id: crypto.randomUUID(), ...input }),
  });
  return response.quote;
}

export async function updateProfessionalQuoteStatus(workspaceId: string, quoteId: string, status: 'sent' | 'accepted' | 'rejected' | 'expired') {
  const response = await apiFetch<{ quote: QuoteMutation }>(`/api/v1/professional/quotes/${encodeURIComponent(quoteId)}/status`, {
    method: 'PATCH', workspaceId, body: JSON.stringify({ status }),
  });
  return response.quote;
}

export async function convertProfessionalQuote(workspaceId: string, quoteId: string, input: {
  customer_site_id?: string;
  field_id?: string;
  type: string;
  occurred_on: string;
  title?: string;
  notes?: string;
  labor_cost_eur?: number;
  other_cost_eur?: number;
}) {
  const participants = input.labor_cost_eur && input.labor_cost_eur > 0
    ? [{ display_name: 'Mano de obra', role: 'coste real', cost_eur: input.labor_cost_eur }]
    : [];
  const resources = input.other_cost_eur && input.other_cost_eur > 0
    ? [{ kind: 'service', name: 'Otros costes reales', cost_eur: input.other_cost_eur }]
    : [];
  const response = await apiFetch<{ work: ConvertedWork }>(`/api/v1/professional/quotes/${encodeURIComponent(quoteId)}/convert`, {
    method: 'POST', workspaceId,
    body: JSON.stringify({
      client_operation_id: crypto.randomUUID(),
      entity_id: crypto.randomUUID(),
      customer_site_id: input.customer_site_id,
      field_id: input.field_id,
      type: input.type,
      occurred_on: input.occurred_on,
      title: input.title,
      notes: input.notes,
      participants,
      resources,
    }),
  });
  return response.work;
}
