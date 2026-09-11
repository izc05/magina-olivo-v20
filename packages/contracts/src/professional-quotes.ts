import { z } from 'zod';
import { clientOperationSchema, isoDateSchema, moneySchema, uuidSchema } from './mi-campo.js';
import { workParticipantSchema, workResourceSchema, workTypeSchema } from './work.js';

export const professionalQuoteStatusSchema = z.enum(['draft','sent','accepted','rejected','expired','converted']);

export const professionalQuoteLineSchema = z.object({
  description: z.string().trim().min(1).max(500),
  quantity: z.number().positive().max(1_000_000).default(1),
  unit: z.string().trim().max(80).optional(),
  unit_price_eur: moneySchema,
  line_total_eur: moneySchema,
});

export const createProfessionalQuoteSchema = clientOperationSchema.extend({
  customer_party_id: uuidSchema,
  customer_site_id: uuidSchema.optional(),
  quote_number: z.string().trim().min(1).max(160).optional(),
  title: z.string().trim().min(1).max(300),
  issued_on: isoDateSchema.optional(),
  valid_until: isoDateSchema.optional(),
  status: z.enum(['draft','sent']).default('draft'),
  subtotal_eur: moneySchema,
  tax_eur: moneySchema.default(0),
  total_eur: moneySchema,
  notes: z.string().trim().max(4000).optional(),
  lines: z.array(professionalQuoteLineSchema).min(1).max(100),
}).superRefine((value, ctx) => {
  const lineTotal = value.lines.reduce((sum, line) => sum + line.line_total_eur, 0);
  if (Math.abs(lineTotal - value.subtotal_eur) > 0.02) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['subtotal_eur'], message: 'quote lines must match subtotal_eur' });
  }
  if (Math.abs((value.subtotal_eur + value.tax_eur) - value.total_eur) > 0.02) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['total_eur'], message: 'subtotal + tax must match total' });
  }
});

export const updateProfessionalQuoteStatusSchema = z.object({
  status: z.enum(['sent','accepted','rejected','expired']),
});

export const convertProfessionalQuoteSchema = clientOperationSchema.extend({
  field_id: uuidSchema.optional(),
  customer_site_id: uuidSchema.optional(),
  campaign_id: uuidSchema.optional(),
  type: workTypeSchema,
  occurred_on: isoDateSchema,
  title: z.string().trim().min(1).max(300).optional(),
  notes: z.string().trim().max(4000).optional(),
  participants: z.array(workParticipantSchema).max(200).default([]),
  resources: z.array(workResourceSchema).max(200).default([]),
}).superRefine((value, ctx) => {
  const destinations = Number(Boolean(value.field_id)) + Number(Boolean(value.customer_site_id));
  if (destinations !== 1) ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['field_id'], message: 'Exactly one work destination is required' });
});

export type CreateProfessionalQuoteInput = z.infer<typeof createProfessionalQuoteSchema>;
export type ConvertProfessionalQuoteInput = z.infer<typeof convertProfessionalQuoteSchema>;
