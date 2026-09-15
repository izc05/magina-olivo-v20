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

export type PublicShareAccessKind = 'missing' | 'expired' | 'revoked' | 'unavailable';

export class PublicShareAccessError extends Error {
  kind: PublicShareAccessKind;
  status: number | null;

  constructor(kind: PublicShareAccessKind, status: number | null = null) {
    super(kind);
    this.name = 'PublicShareAccessError';
    this.kind = kind;
    this.status = status;
  }
}

type PublicErrorPayload = { error?: string };

function classifyAccessError(status: number, payload: unknown): PublicShareAccessKind {
  const error = typeof payload === 'object' && payload !== null && 'error' in payload
    ? String((payload as PublicErrorPayload).error ?? '')
    : '';
  if (error === 'share_link_expired') return 'expired';
  if (error === 'share_link_revoked') return 'revoked';
  if (status === 404) return 'missing';
  return 'unavailable';
}

async function publicFetch<T>(path: string, init?: RequestInit) {
  if (!apiBaseUrl) throw new PublicShareAccessError('unavailable');
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: { 'content-type': 'application/json', ...(init?.headers ?? {}) },
    });
  } catch {
    throw new PublicShareAccessError('unavailable');
  }
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new PublicShareAccessError(classifyAccessError(response.status, payload), response.status);
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
