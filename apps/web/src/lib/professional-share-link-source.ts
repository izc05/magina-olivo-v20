import { apiBaseUrl, apiFetch } from '@/lib/api-client';
import type { CommercialEntityType } from '@/lib/professional-delivery-source';

export type ProfessionalShareLink = {
  id: string;
  entity_type: CommercialEntityType;
  entity_id: string;
  document_id: string;
  expires_at: string;
  revoked_at: string | null;
  access_count: number;
  last_accessed_at: string | null;
  created_at: string;
};

export async function createProfessionalShareLink(input: {
  workspaceId: string;
  entityType: CommercialEntityType;
  entityId: string;
  documentId: string;
  expiresInDays?: number;
}) {
  const result = await apiFetch<{ share: ProfessionalShareLink; token: string; path: string }>('/api/v1/professional/share-links', {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({
      entity_type: input.entityType,
      entity_id: input.entityId,
      document_id: input.documentId,
      expires_in_days: input.expiresInDays ?? 7,
    }),
  });
  return { ...result, url: `${apiBaseUrl}${result.path}` };
}

export async function loadProfessionalShareLinks(workspaceId: string, entityType: CommercialEntityType, entityId: string) {
  const params = new URLSearchParams({ entityType, entityId });
  const result = await apiFetch<{ links: ProfessionalShareLink[] }>(`/api/v1/professional/share-links?${params.toString()}`, { workspaceId });
  return result.links;
}

export async function revokeProfessionalShareLink(workspaceId: string, shareId: string) {
  return apiFetch(`/api/v1/professional/share-links/${encodeURIComponent(shareId)}`, { method: 'DELETE', workspaceId });
}
