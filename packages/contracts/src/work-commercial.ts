import { z } from 'zod';
import { clientOperationSchema, isoDateSchema, moneySchema } from './mi-campo.js';

export const updateWorkCommercialSchema = z.object({
  quoted_amount_eur: moneySchema.optional(),
  charge_eur: moneySchema.optional(),
  collected_eur: moneySchema.optional(),
  payment_status: z.enum(['pending', 'partial', 'paid']).optional(),
  invoice_reference: z.string().trim().max(160).optional(),
}).superRefine((value, ctx) => {
  if (value.charge_eur !== undefined && value.collected_eur !== undefined && value.collected_eur > value.charge_eur) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['collected_eur'], message: 'collected_eur cannot exceed charge_eur' });
  }
});

export const createWorkCollectionSchema = clientOperationSchema.extend({
  collected_on: isoDateSchema,
  amount_eur: moneySchema.refine((value) => value > 0, 'amount_eur must be greater than zero'),
  method: z.enum(['cash', 'bank', 'card', 'bizum', 'other']).optional(),
  reference: z.string().trim().max(240).optional(),
  notes: z.string().trim().max(2000).optional(),
});

export type UpdateWorkCommercialInput = z.infer<typeof updateWorkCommercialSchema>;
export type CreateWorkCollectionInput = z.infer<typeof createWorkCollectionSchema>;
