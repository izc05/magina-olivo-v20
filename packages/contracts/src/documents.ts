import { z } from 'zod';
import { clientOperationSchema, uuidSchema } from './mi-campo.js';

export const documentKindSchema = z.enum([
  'delivery_ticket',
  'yield_result',
  'settlement_statement',
  'collection_receipt',
  'invoice',
  'sales_invoice',
  'purchase_receipt',
  'quote',
  'treatment',
  'fertilization',
  'irrigation',
  'pruning',
  'observation',
  'work_report',
  'land_reference',
  'photo',
  'other',
]);

export const createDocumentSchema = clientOperationSchema.extend({
  version_id: uuidSchema.optional(),
  field_id: uuidSchema.optional(),
  campaign_id: uuidSchema.optional(),
  domain_type: z.string().trim().min(1).max(120).optional(),
  domain_record_id: uuidSchema.optional(),
  relation: z.string().trim().min(1).max(80).default('attachment'),
  kind: documentKindSchema,
  title: z.string().trim().min(1).max(240),
  original_filename: z.string().trim().min(1).max(255),
  mime_type: z.string().trim().min(1).max(160),
  byte_size: z.number().int().nonnegative().max(100 * 1024 * 1024),
  sha256: z.string().regex(/^[0-9a-fA-F]{64}$/),
}).superRefine((value, context) => {
  if (!value.field_id && !value.domain_record_id) {
    context.addIssue({ code: 'custom', path: ['field_id'], message: 'field_id or domain_record_id is required' });
  }
  if (value.domain_record_id && !value.domain_type) {
    context.addIssue({ code: 'custom', path: ['domain_type'], message: 'domain_type is required with domain_record_id' });
  }
});

export const requestOcrSchema = clientOperationSchema.extend({
  document_version_id: uuidSchema,
  preferred_provider: z.enum(['auto', 'tesseract', 'paddleocr', 'doctr']).default('auto'),
});

export const confirmExtractionFieldSchema = z.object({
  extraction_run_id: uuidSchema,
  confirmed_fields: z.record(z.string(), z.unknown()),
  corrections: z.record(z.string(), z.unknown()).optional(),
});

export type CreateDocumentInput = z.infer<typeof createDocumentSchema>;
export type RequestOcrInput = z.infer<typeof requestOcrSchema>;
export type ConfirmExtractionInput = z.infer<typeof confirmExtractionFieldSchema>;
