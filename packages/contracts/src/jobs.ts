import { z } from 'zod';
import { uuidSchema } from './mi-campo.js';

export const OCR_QUEUE_NAME = 'magina-ocr-v1' as const;
export const OCR_DEAD_LETTER_QUEUE_NAME = 'magina-ocr-dlq-v1' as const;
export const RADAR_INGEST_QUEUE_NAME = 'magina-radar-ingest-v1' as const;
export const RADAR_INGEST_DEAD_LETTER_QUEUE_NAME = 'magina-radar-ingest-dlq-v1' as const;
export const NOTIFICATION_DISPATCH_QUEUE_NAME = 'magina-notification-dispatch-v1' as const;
export const NOTIFICATION_DISPATCH_DEAD_LETTER_QUEUE_NAME = 'magina-notification-dispatch-dlq-v1' as const;

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

export const radarIngestJobPayloadSchema = z.object({
  version: z.literal(1),
  source: z.literal('aemet_national_mosaic'),
  product: z.literal('reflectivity'),
  requested_at: z.string().datetime({ offset: true }),
});

export const notificationDispatchJobPayloadSchema = z.object({
  version: z.literal(1),
  intent_id: uuidSchema.optional(),
  limit: z.number().int().min(1).max(100).default(50),
});

export type OcrProviderName = z.infer<typeof ocrProviderNameSchema>;
export type OcrJobPayload = z.infer<typeof ocrJobPayloadSchema>;
export type RadarIngestJobPayload = z.infer<typeof radarIngestJobPayloadSchema>;
export type NotificationDispatchJobPayload = z.infer<typeof notificationDispatchJobPayloadSchema>;
