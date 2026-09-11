import { apiFetch } from '@/lib/api-client';

export type CommercialEntityType = 'professional_quote' | 'professional_invoice';
export type CommercialDeliveryChannel = 'email' | 'whatsapp' | 'share' | 'link' | 'other';
export type CommercialDelivery = {
  id: string;
  entity_type: CommercialEntityType;
  entity_id: string;
  document_id: string | null;
  channel: CommercialDeliveryChannel;
  recipient: string | null;
  status: 'prepared' | 'confirmed_sent' | 'cancelled';
  prepared_at: string;
  confirmed_sent_at: string | null;
  note: string | null;
  document_title?: string | null;
  document_kind?: string | null;
};

export async function loadCommercialDeliveries(workspaceId: string, entityType: CommercialEntityType, entityId: string) {
  const params = new URLSearchParams({ entityType, entityId });
  const result = await apiFetch<{ deliveries: CommercialDelivery[] }>(`/api/v1/professional/deliveries?${params.toString()}`, { workspaceId });
  return result.deliveries;
}

export async function prepareCommercialDelivery(input: {
  workspaceId: string;
  entityType: CommercialEntityType;
  entityId: string;
  documentId?: string;
  channel: CommercialDeliveryChannel;
  recipient?: string;
  note?: string;
}) {
  const result = await apiFetch<{ delivery: CommercialDelivery }>('/api/v1/professional/deliveries', {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({
      client_operation_id: crypto.randomUUID(),
      entity_type: input.entityType,
      entity_id: input.entityId,
      document_id: input.documentId,
      channel: input.channel,
      recipient: input.recipient || undefined,
      note: input.note || undefined,
    }),
  });
  return result.delivery;
}

export async function confirmCommercialDelivery(workspaceId: string, deliveryId: string, confirmedSent = true) {
  const result = await apiFetch<{ delivery: CommercialDelivery }>(`/api/v1/professional/deliveries/${encodeURIComponent(deliveryId)}/confirm`, {
    method: 'PATCH',
    workspaceId,
    body: JSON.stringify({ confirmed_sent: confirmedSent }),
  });
  return result.delivery;
}

export async function recordQuoteDecision(input: {
  workspaceId: string;
  quoteId: string;
  decision: 'accepted' | 'rejected';
  deliveryId?: string;
  note?: string;
}) {
  return apiFetch(`/api/v1/professional/quotes/${encodeURIComponent(input.quoteId)}/decision`, {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({ decision: input.decision, delivery_id: input.deliveryId, note: input.note }),
  });
}
