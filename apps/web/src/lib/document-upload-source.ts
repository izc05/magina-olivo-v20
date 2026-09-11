import { apiFetch } from '@/lib/api-client';

export type DocumentKind = 'delivery_ticket' | 'yield_result' | 'invoice' | 'treatment' | 'fertilization' | 'irrigation' | 'work_report' | 'land_reference' | 'photo' | 'other';

type ReserveResponse = {
  document: { id: string };
  version: { id: string };
  upload: {
    uploadUrl: string;
    method: 'PUT';
    headers: Record<string, string>;
    expiresAt: string;
  };
};

function bytesToHex(bytes: ArrayBuffer) {
  return Array.from(new Uint8Array(bytes)).map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function sha256(file: File) {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return bytesToHex(digest);
}

export async function uploadDomainAttachment(input: {
  workspaceId: string;
  fieldId?: string;
  domainType?: string;
  domainRecordId?: string;
  file: File;
  kind: DocumentKind;
  title: string;
  relation?: string;
}) {
  if (!input.file.size) throw new Error('empty_file');
  if (input.file.size > 100 * 1024 * 1024) throw new Error('file_too_large');

  const checksum = await sha256(input.file);
  const reserved = await apiFetch<ReserveResponse>('/api/v1/documents', {
    method: 'POST',
    workspaceId: input.workspaceId,
    body: JSON.stringify({
      client_operation_id: crypto.randomUUID(),
      field_id: input.fieldId,
      domain_type: input.domainType,
      domain_record_id: input.domainRecordId,
      relation: input.relation ?? 'evidence',
      kind: input.kind,
      title: input.title,
      original_filename: input.file.name,
      mime_type: input.file.type || 'application/octet-stream',
      byte_size: input.file.size,
      sha256: checksum,
    }),
  });

  const uploadResponse = await fetch(reserved.upload.uploadUrl, {
    method: reserved.upload.method,
    headers: reserved.upload.headers,
    body: input.file,
  });
  if (!uploadResponse.ok) throw new Error(`storage_upload_failed:${uploadResponse.status}`);

  await apiFetch(`/api/v1/documents/${encodeURIComponent(reserved.document.id)}/versions/${encodeURIComponent(reserved.version.id)}/complete`, {
    method: 'POST',
    workspaceId: input.workspaceId,
  });

  return { documentId: reserved.document.id, versionId: reserved.version.id };
}
