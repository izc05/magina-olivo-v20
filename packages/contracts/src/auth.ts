import { z } from 'zod';

export const googleSignInSchema = z.object({
  credential: z.string().trim().min(100).max(20_000),
});

export const selectWorkspaceSchema = z.object({
  workspace_id: z.string().uuid(),
});

export const publicProfileRoleSchema = z.enum([
  'agricultor',
  'propietario',
  'trabajador',
  'profesional_agricola',
  'tecnico',
  'empresa',
  'otro',
]);

export const updateMyProfileSchema = z.object({
  display_name: z.string().trim().min(2).max(80).nullable().optional(),
  municipality: z.string().trim().max(120).nullable().optional(),
  bio: z.string().trim().max(500).nullable().optional(),
  public_role: publicProfileRoleSchema.nullable().optional(),
  visibility: z.enum(['private', 'public']).optional(),
}).refine((value) => Object.keys(value).length > 0, { message: 'At least one profile field is required.' });

export const updateMyPreferencesSchema = z.object({
  theme: z.enum(['system', 'light', 'dark']).optional(),
  unit_system: z.literal('metric').optional(),
  preferred_municipality: z.string().trim().max(120).nullable().optional(),
  locale: z.string().trim().min(2).max(20).optional(),
  community_notifications: z.boolean().optional(),
  weather_alerts: z.boolean().optional(),
}).refine((value) => Object.keys(value).length > 0, { message: 'At least one preference is required.' });

export type GoogleSignInInput = z.infer<typeof googleSignInSchema>;
export type SelectWorkspaceInput = z.infer<typeof selectWorkspaceSchema>;
export type UpdateMyProfileInput = z.infer<typeof updateMyProfileSchema>;
export type UpdateMyPreferencesInput = z.infer<typeof updateMyPreferencesSchema>;
