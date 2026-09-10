import { z } from 'zod';
import { clientOperationSchema, isoDateSchema, moneySchema, uuidSchema } from './mi-campo.js';

export const partyKindSchema = z.enum(['person', 'organization']);
export const partyRoleSchema = z.enum([
  'owner', 'family', 'worker', 'contractor', 'customer', 'supplier', 'cooperative', 'mill', 'other',
]);

export const createPartySchema = clientOperationSchema.extend({
  kind: partyKindSchema,
  display_name: z.string().trim().min(1).max(240),
  legal_name: z.string().trim().max(300).optional(),
  tax_id: z.string().trim().max(40).optional(),
  phone: z.string().trim().max(60).optional(),
  email: z.string().trim().email().max(320).optional(),
  roles: z.array(partyRoleSchema).min(1).max(12),
  notes: z.string().trim().max(4000).optional(),
});

export const workUnitSchema = z.enum(['hours', 'days', 'jornales', 'units', 'fixed']);
export const workTypeSchema = z.enum([
  'pruning', 'shredding', 'harvest', 'treatment', 'fertilization', 'irrigation',
  'mowing', 'tillage', 'transport', 'manual-work', 'machinery-work', 'other',
]);

export const createCrewSchema = clientOperationSchema.extend({
  name: z.string().trim().min(1).max(240),
  leader_party_id: uuidSchema.optional(),
  member_party_ids: z.array(uuidSchema).max(200).default([]),
  default_rate_eur: moneySchema.optional(),
  default_rate_unit: workUnitSchema.optional(),
  notes: z.string().trim().max(4000).optional(),
});

export const createMachinerySchema = clientOperationSchema.extend({
  name: z.string().trim().min(1).max(240),
  category: z.string().trim().max(160).optional(),
  ownership: z.enum(['owned', 'rented', 'third-party-service']),
  owner_party_id: uuidSchema.optional(),
  registration_or_serial: z.string().trim().max(160).optional(),
  default_rate_eur: moneySchema.optional(),
  default_rate_unit: workUnitSchema.optional(),
  notes: z.string().trim().max(4000).optional(),
});

export const createMaterialSchema = clientOperationSchema.extend({
  name: z.string().trim().min(1).max(240),
  category: z.string().trim().max(160).optional(),
  default_unit: z.string().trim().max(80).optional(),
  default_unit_cost_eur: moneySchema.optional(),
  supplier_party_id: uuidSchema.optional(),
  notes: z.string().trim().max(4000).optional(),
});

export const workParticipantSchema = z.object({
  party_id: uuidSchema.optional(),
  crew_id: uuidSchema.optional(),
  display_name: z.string().trim().min(1).max(240),
  role: z.string().trim().max(120).optional(),
  quantity: z.number().positive().max(1_000_000).optional(),
  unit: workUnitSchema.optional(),
  rate_eur: moneySchema.optional(),
  cost_eur: moneySchema.optional(),
}).refine((value) => !(value.party_id && value.crew_id), {
  message: 'Use either party_id or crew_id for a participant, not both',
  path: ['party_id'],
});

export const workResourceSchema = z.object({
  kind: z.enum(['machinery', 'material', 'service']),
  machinery_id: uuidSchema.optional(),
  material_id: uuidSchema.optional(),
  supplier_party_id: uuidSchema.optional(),
  name: z.string().trim().min(1).max(240),
  quantity: z.number().positive().max(1_000_000).optional(),
  unit: z.string().trim().max(80).optional(),
  unit_cost_eur: moneySchema.optional(),
  cost_eur: moneySchema.optional(),
});

export const createWorkSchema = clientOperationSchema.extend({
  field_id: uuidSchema,
  campaign_id: uuidSchema.optional(),
  type: workTypeSchema,
  occurred_on: isoDateSchema,
  title: z.string().trim().min(1).max(300),
  notes: z.string().trim().max(4000).optional(),
  performed_for: z.enum(['self', 'third-party']).default('self'),
  customer_party_id: uuidSchema.optional(),
  quoted_amount_eur: moneySchema.optional(),
  charge_eur: moneySchema.optional(),
  participants: z.array(workParticipantSchema).max(200).default([]),
  resources: z.array(workResourceSchema).max(200).default([]),
}).superRefine((value, ctx) => {
  if (value.performed_for === 'third-party' && !value.customer_party_id) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, path: ['customer_party_id'], message: 'customer_party_id is required for third-party work' });
  }
});

export type CreatePartyInput = z.infer<typeof createPartySchema>;
export type CreateCrewInput = z.infer<typeof createCrewSchema>;
export type CreateMachineryInput = z.infer<typeof createMachinerySchema>;
export type CreateMaterialInput = z.infer<typeof createMaterialSchema>;
export type CreateWorkInput = z.infer<typeof createWorkSchema>;
