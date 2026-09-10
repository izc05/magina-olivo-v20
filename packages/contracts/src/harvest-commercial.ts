import { z } from 'zod';
import { clientOperationSchema, isoDateSchema, moneySchema, uuidSchema } from './mi-campo.js';

export const createHarvestSettlementSchema = clientOperationSchema.extend({
  campaign_id: uuidSchema.optional(),
  counterparty_name: z.string().trim().max(240).optional(),
  settlement_number: z.string().trim().max(160).optional(),
  settled_on: isoDateSchema,
  basis: z.enum(['olive_kg', 'oil_kg', 'fixed']).default('olive_kg'),
  unit_price_eur: z.number().finite().nonnegative().max(1_000_000).optional(),
  gross_eur: moneySchema,
  deductions_eur: moneySchema.default(0),
  net_eur: moneySchema,
  delivery_ids: z.array(uuidSchema).min(1).max(500),
  notes: z.string().trim().max(4000).optional(),
}).superRefine((value, ctx) => {
  if (Math.abs((value.gross_eur - value.deductions_eur) - value.net_eur) > 0.02) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['net_eur'], message: 'net_eur must equal gross_eur minus deductions_eur' });
  }
  if (new Set(value.delivery_ids).size !== value.delivery_ids.length) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['delivery_ids'], message: 'delivery_ids cannot contain duplicates' });
  }
});

export const createHarvestCollectionSchema = clientOperationSchema.extend({
  collected_on: isoDateSchema,
  amount_eur: moneySchema.refine((value) => value > 0, 'amount_eur must be greater than zero'),
  method: z.enum(['cash', 'bank', 'card', 'bizum', 'other']).optional(),
  reference: z.string().trim().max(200).optional(),
  notes: z.string().trim().max(4000).optional(),
});

export type CreateHarvestSettlementInput = z.infer<typeof createHarvestSettlementSchema>;
export type CreateHarvestCollectionInput = z.infer<typeof createHarvestCollectionSchema>;
