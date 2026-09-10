import { z } from 'zod';
import { moneySchema } from './mi-campo.js';
import { workPaymentStatusSchema } from './work.js';

export const updateWorkCommercialSchema = z.object({
  quoted_amount_eur: moneySchema.optional(),
  charge_eur: moneySchema.optional(),
  collected_eur: moneySchema.optional(),
  payment_status: workPaymentStatusSchema,
  invoice_reference: z.string().trim().max(160).optional(),
}).superRefine((value, ctx) => {
  if (value.charge_eur !== undefined && value.collected_eur !== undefined && value.collected_eur > value.charge_eur) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['collected_eur'], message: 'collected_eur cannot exceed charge_eur' });
  }
  if (value.payment_status === 'paid' && value.charge_eur !== undefined && value.collected_eur !== value.charge_eur) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['payment_status'], message: 'paid requires collected_eur to equal charge_eur' });
  }
});

export type UpdateWorkCommercialInput = z.infer<typeof updateWorkCommercialSchema>;
