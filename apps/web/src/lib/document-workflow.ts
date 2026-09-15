import type { DocumentAnalysis } from '@/lib/document-data-source';
import type { DocumentKind } from '@/lib/document-upload-source';

export const documentKinds: Array<{ value: DocumentKind; label: string }> = [
  { value: 'invoice', label: 'Factura / gasto' },
  { value: 'purchase_receipt', label: 'Ticket / compra' },
  { value: 'quote', label: 'Presupuesto' },
  { value: 'delivery_ticket', label: 'Albarán de entrega' },
  { value: 'yield_result', label: 'Resultado de rendimiento' },
  { value: 'settlement_statement', label: 'Liquidación' },
  { value: 'collection_receipt', label: 'Justificante de cobro' },
  { value: 'treatment', label: 'Tratamiento / fitosanitario' },
  { value: 'fertilization', label: 'Abonado' },
  { value: 'irrigation', label: 'Riego' },
  { value: 'pruning', label: 'Poda' },
  { value: 'observation', label: 'Observación' },
  { value: 'work_report', label: 'Parte de trabajo' },
  { value: 'land_reference', label: 'Terreno / referencia' },
  { value: 'photo', label: 'Fotografía' },
  { value: 'other', label: 'Otro' },
];

export const ocrDocumentKinds = new Set<DocumentKind>([
  'invoice',
  'purchase_receipt',
  'quote',
  'delivery_ticket',
  'yield_result',
  'settlement_statement',
  'collection_receipt',
]);

export function documentKindLabel(kind: string) {
  return documentKinds.find((item) => item.value === kind)?.label ?? 'Documento';
}

export function supportsDocumentOcr(kind: string): kind is DocumentKind {
  return ocrDocumentKinds.has(kind as DocumentKind);
}

export function buildDocumentReviewHref(documentId: string, fieldId: string, source: string) {
  const params = new URLSearchParams({ documentId, fieldId, source });
  return `/mi-campo/documentos/revisar?${params.toString()}`;
}

export type DocumentWorkflowState = 'available' | 'processing' | 'needs_review' | 'confirmed' | 'failed';

export function documentWorkflowState(analysis: DocumentAnalysis): DocumentWorkflowState {
  if (analysis.review) return 'confirmed';
  if (analysis.extraction?.status === 'needs_review' || analysis.extraction?.status === 'succeeded') return 'needs_review';
  if (analysis.ocr?.status === 'failed' || analysis.extraction?.status === 'failed' || analysis.version?.integrity_status === 'failed') return 'failed';
  if (analysis.ocr?.status === 'queued' || analysis.ocr?.status === 'processing' || analysis.extraction?.status === 'queued' || analysis.extraction?.status === 'processing') return 'processing';
  return 'available';
}

export const workflowStateLabels: Record<DocumentWorkflowState, string> = {
  available: 'Archivo disponible',
  processing: 'Leyendo documento',
  needs_review: 'Pendiente de revisión',
  confirmed: 'Datos revisados',
  failed: 'Revisar error',
};
