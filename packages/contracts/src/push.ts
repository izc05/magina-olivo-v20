import { z } from 'zod';
import { uuidSchema } from './mi-campo.js';

const base64UrlSchema = z.string().min(8).max(1024).regex(/^[A-Za-z0-9_-]+$/);

export const pushEndpointSchema = z.string().url().max(2048).refine((value) => {
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}, 'Push endpoint must use HTTPS');

export const registerPushSubscriptionSchema = z.object({
  endpoint: pushEndpointSchema,
  expiration_time: z.number().int().positive().nullable().optional(),
  keys: z.object({
    p256dh: base64UrlSchema.min(16),
    auth: base64UrlSchema,
  }),
  device_label: z.string().trim().min(1).max(120).optional(),
});

export const unregisterPushSubscriptionSchema = z.object({
  endpoint: pushEndpointSchema,
});

export const pushTestSchema = z.object({
  field_id: uuidSchema.optional(),
});

export type RegisterPushSubscriptionInput = z.infer<typeof registerPushSubscriptionSchema>;
export type UnregisterPushSubscriptionInput = z.infer<typeof unregisterPushSubscriptionSchema>;
export type PushTestInput = z.infer<typeof pushTestSchema>;
