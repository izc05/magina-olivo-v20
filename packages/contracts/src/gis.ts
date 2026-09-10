import { z } from 'zod';

export const gisBboxQuerySchema = z.object({
  minLon: z.coerce.number().min(-180).max(180),
  minLat: z.coerce.number().min(-90).max(90),
  maxLon: z.coerce.number().min(-180).max(180),
  maxLat: z.coerce.number().min(-90).max(90),
});

export const catastroReferenceSchema = z.object({
  reference: z.string().trim().toUpperCase().regex(/^[A-Z0-9]{14}$/),
});

export const sigpacFeatureIdSchema = z.object({
  featureId: z.string().trim().regex(/^\d{1,20}$/),
});

export const linkCatastroReferenceSchema = z.object({
  reference: z.string().trim().toUpperCase().regex(/^[A-Z0-9]{14}$/),
  set_as_geometry: z.boolean().default(false),
});

export const linkSigpacReferenceSchema = z.object({
  feature_id: z.string().trim().regex(/^\d{1,20}$/),
  set_as_geometry: z.boolean().default(false),
});

export type GisBboxQuery = z.infer<typeof gisBboxQuerySchema>;
export type LinkCatastroReferenceInput = z.infer<typeof linkCatastroReferenceSchema>;
export type LinkSigpacReferenceInput = z.infer<typeof linkSigpacReferenceSchema>;
