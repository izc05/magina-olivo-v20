import { z } from 'zod';
import { uuidSchema } from './mi-campo.js';

export const OCR_QUEUE_NAME = 'magina-ocr-v1' as const;
export const OCR_DEAD_LETTER_QUEUE_NAME = 'magina-ocr-dlq-v1' as const;

export const ocrProviderNameSchema = z.enum(['tesseract', 'paddleocr', 'doctr']);

export const ocrJobPayloadSchema = z.object({
  version: z.literal(1),
  ocr_run_id: uuidSchema,
  document_version_id: uuidSchema,
  storage_key: z.string().min(1).max(1024),
  mime_type: z.string().min(1).max(160),
  expected_sha256_hex: z.string().regex(/^[0-9a-f]{64}$/),
  preferred_provider: z.union([z.literal('auto'), ocrProviderNameSchema]),
});

export type OcrProviderName = z.infer<typeof ocrProviderNameSchema>;
export type OcrJobPayload = z.infer<typeof ocrJobPayloadSchema>;
