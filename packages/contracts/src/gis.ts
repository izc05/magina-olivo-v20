import { z } from 'zod';

export const gisBboxQuerySchema = z.object({
  minLon: z.coerce.number().min(-180).max(180),
  minLat: z.coerce.number().min(-90).max(90),
  maxLon: z.coerce.number().min(-180).max(180),
  maxLat: z.coerce.number().min(-90).max(90),
}).superRefine((bbox, ctx) => {
  if (bbox.minLon >= bbox.maxLon || bbox.minLat >= bbox.maxLat) {
    ctx.addIssue({ code: 'custom', message: 'bbox must have positive width and height' });
  }
  if (bbox.maxLon - bbox.minLon > 0.05 || bbox.maxLat - bbox.minLat > 0.05) {
    ctx.addIssue({ code: 'custom', message: 'bbox exceeds the V20 maximum span of 0.05 degrees' });
  }
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
