import { z } from 'zod';

export const uuidSchema = z.string().uuid();
export const isoDateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Expected YYYY-MM-DD');
export const isoDateTimeSchema = z.string().datetime({ offset: true });
export const moneySchema = z.number().finite().nonnegative().max(99_999_999.99);

export const clientOperationSchema = z.object({
  client_operation_id: uuidSchema,
  entity_id: uuidSchema.optional(),
});

export const createFieldSchema = clientOperationSchema.extend({
  name: z.string().trim().min(1).max(120),
  municipality: z.string().trim().min(1).max(120).optional(),
  province: z.string().trim().min(1).max(120).optional(),
  tree_count: z.number().int().positive().max(1_000_000).optional(),
  variety: z.string().trim().max(120).optional(),
  water_regime: z.enum(['secano', 'regadio', 'mixto']).optional(),
});

export const followUpSchema = z.object({
  scheduled_at: isoDateTimeSchema,
  reminder_offsets_minutes: z.array(z.number().int().positive().max(525_600)).max(8).optional(),
}).optional();

export const createIrrigationSchema = clientOperationSchema.extend({
  occurred_at: isoDateTimeSchema,
  duration_hours: z.number().positive().max(10_000).optional(),
  water_m3: z.number().nonnegative().max(1_000_000_000).optional(),
  cost_eur: moneySchema.optional(),
  notes: z.string().trim().max(4_000).optional(),
  follow_up: followUpSchema,
});

export const createTreatmentSchema = clientOperationSchema.extend({
  occurred_at: isoDateTimeSchema,
  reason: z.string().trim().min(1).max(200),
  product_name: z.string().trim().min(1).max(240),
  dose: z.string().trim().max(200).optional(),
  quantity: z.string().trim().max(200).optional(),
  applicator: z.string().trim().max(200).optional(),
  equipment: z.string().trim().max(200).optional(),
  cost_eur: moneySchema.optional(),
  notes: z.string().trim().max(4_000).optional(),
  follow_up: followUpSchema,
});

export const createFertilizationSchema = clientOperationSchema.extend({
  occurred_at: isoDateTimeSchema,
  product_name: z.string().trim().min(1).max(240),
  quantity_kg: z.number().positive().max(1_000_000).optional(),
  application_method: z.string().trim().max(120).optional(),
  composition: z.string().trim().max(200).optional(),
  cost_eur: moneySchema.optional(),
  supplier: z.string().trim().max(240).optional(),
  notes: z.string().trim().max(4_000).optional(),
});

export const createPruningSchema = clientOperationSchema.extend({
  occurred_at: isoDateTimeSchema,
  pruning_type: z.string().trim().min(1).max(160),
  workers: z.number().int().positive().max(10_000).optional(),
  hours: z.number().positive().max(1_000_000).optional(),
  cost_eur: moneySchema.optional(),
  notes: z.string().trim().max(4_000).optional(),
  follow_up: followUpSchema,
});

export const createExpenseSchema = clientOperationSchema.extend({
  occurred_on: isoDateSchema,
  category: z.string().trim().min(1).max(120),
  concept: z.string().trim().min(1).max(300),
  amount_eur: moneySchema,
  notes: z.string().trim().max(4_000).optional(),
});

export const deliveryFieldAllocationSchema = z.object({
  field_id: uuidSchema,
  kg: z.number().positive().max(100_000_000).optional(),
});

export const createDeliverySchema = clientOperationSchema.extend({
  campaign_id: uuidSchema.optional(),
  cooperative_or_mill: z.string().trim().max(240).optional(),
  delivery_at: isoDateTimeSchema,
  ticket_number: z.string().trim().max(160).optional(),
  total_kg: z.number().positive().max(100_000_000),
  source: z.enum(['manual', 'ocr', 'import']),
  fields: z.array(deliveryFieldAllocationSchema).min(1).max(100),
});

export const createDeliveryResultSchema = clientOperationSchema.extend({
  result_date: isoDateSchema,
  yield_percent: z.number().finite().min(0).max(100),
  moisture_percent: z.number().finite().min(0).max(100).optional(),
  acidity_percent: z.number().finite().min(0).max(100).optional(),
});

export type CreateFieldInput = z.infer<typeof createFieldSchema>;
export type CreateIrrigationInput = z.infer<typeof createIrrigationSchema>;
export type CreateTreatmentInput = z.infer<typeof createTreatmentSchema>;
export type CreateFertilizationInput = z.infer<typeof createFertilizationSchema>;
export type CreatePruningInput = z.infer<typeof createPruningSchema>;
export type CreateExpenseInput = z.infer<typeof createExpenseSchema>;
export type CreateDeliveryInput = z.infer<typeof createDeliverySchema>;
export type CreateDeliveryResultInput = z.infer<typeof createDeliveryResultSchema>;
