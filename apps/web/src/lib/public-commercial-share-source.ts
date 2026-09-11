import { apiBaseUrl } from '@/lib/api-client';

export type PublicCommercialShare = {
  entity_type: 'professional_quote' | 'professional_invoice';
  document_id: string;
  expires_at: string;
  status: string | null;
  title: string | null;
  number: string | null;
  issued_on: string | null;
  valid_until: string | null;
  total_eur: number;
  issuer: Record<string, unknown> | null;
  customer: Record<string, unknown> | null;
  decision: 'accepted' | 'rejected' | null;
  decided_at: string | null;
  can_decide: boolean;
  file_path: string;
};

async function publicFetch<T>(path: string, init?: RequestInit) {
  if (!apiBaseUrl) throw new Error('api_unavailable');
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: { 'content-type': 'application/json', ...(init?.headers ?? {}) },
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error('public_share_request_failed'), { status: response.status, payload });
  return payload as T;
}

export async function loadPublicCommercialShare(token: string) {
  const result = await publicFetch<{ share: PublicCommercialShare }>(`/api/public/v1/professional/share/${encodeURIComponent(token)}/info`);
  return result.share;
}

export function publicCommercialPdfUrl(token: string) {
  return `${apiBaseUrl}/api/public/v1/professional/share/${encodeURIComponent(token)}/file`;
}

export async function submitPublicQuoteDecision(token: string, input: {
  decision: 'accepted' | 'rejected';
  customerName?: string;
  note?: string;
}) {
  return publicFetch<{ replayed: boolean; decision: { id: string; decision: 'accepted' | 'rejected'; decided_at: string } }>(
    `/api/public/v1/professional/share/${encodeURIComponent(token)}/decision`,
    {
      method: 'POST',
      body: JSON.stringify({
        decision: input.decision,
        customer_name: input.customerName || undefined,
        note: input.note || undefined,
      }),
    },
  );
}
