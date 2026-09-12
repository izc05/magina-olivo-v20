import { z } from 'zod';
import { clientOperationSchema, isoDateSchema, moneySchema, uuidSchema } from './mi-campo.js';

export const professionalInvoiceStatusSchema = z.enum(['draft', 'issued', 'void']);

export const professionalInvoiceWorkSchema = z.object({
  work_id: uuidSchema,
  amount_eur: moneySchema,
});

export const createProfessionalInvoiceSchema = clientOperationSchema.extend({
  customer_party_id: uuidSchema,
  invoice_number: z.string().trim().min(1).max(120).optional(),
  issued_on: isoDateSchema.optional(),
  due_on: isoDateSchema.optional(),
  status: professionalInvoiceStatusSchema.default('draft'),
  subtotal_eur: moneySchema,
  tax_eur: moneySchema.default(0),
  total_eur: moneySchema,
  notes: z.string().trim().max(4000).optional(),
  works: z.array(professionalInvoiceWorkSchema).min(1).max(200),
}).superRefine((value, ctx) => {
  if (Math.abs((value.subtotal_eur + value.tax_eur) - value.total_eur) > 0.01) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['total_eur'], message: 'total_eur must equal subtotal_eur + tax_eur' });
  }
  if (value.status === 'issued' && (!value.invoice_number || !value.issued_on)) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['invoice_number'], message: 'issued invoices require invoice_number and issued_on' });
  }
  const ids = new Set(value.works.map((item) => item.work_id));
  if (ids.size !== value.works.length) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['works'], message: 'work_id values must be unique' });
  }
});

export const updateProfessionalInvoiceSchema = z.object({
  invoice_number: z.string().trim().min(1).max(120).optional(),
  issued_on: isoDateSchema.optional(),
  due_on: isoDateSchema.optional(),
  status: professionalInvoiceStatusSchema.optional(),
  notes: z.string().trim().max(4000).optional(),
}).superRefine((value, ctx) => {
  if (value.status === 'issued' && !value.invoice_number) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['invoice_number'], message: 'invoice_number is required when issuing from the update request' });
  }
});

export type CreateProfessionalInvoiceInput = z.infer<typeof createProfessionalInvoiceSchema>;
export type UpdateProfessionalInvoiceInput = z.infer<typeof updateProfessionalInvoiceSchema>;
