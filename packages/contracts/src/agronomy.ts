import { z } from 'zod';
import { uuidSchema } from './mi-campo.js';

export const weatherEvidenceKindSchema = z.enum([
  'forecast',
  'observation',
  'radar',
  'official-warning',
  'manual',
]);

export const agronomicRiskLevelSchema = z.enum(['none', 'low', 'medium', 'high', 'unknown']);
export const agronomicSuitabilitySchema = z.enum(['good', 'caution', 'avoid', 'unknown']);
export const advisoryConfidenceSchema = z.enum(['low', 'medium', 'high']);
export const advisoryKindSchema = z.enum([
  'treatment-window',
  'irrigation-context',
  'harvest-context',
  'wind-risk',
  'rain-risk',
  'heat-risk',
  'frost-risk',
  'field-access',
  'generic',
]);

export const weatherEvidenceSchema = z.object({
  id: z.string().min(1).max(160),
  kind: weatherEvidenceKindSchema,
  source: z.string().min(1).max(160),
  observed_or_valid_at: z.string().datetime({ offset: true }).nullable(),
  fetched_at: z.string().datetime({ offset: true }),
  stale: z.boolean().default(false),
  summary: z.string().trim().max(500).optional(),
});

export const farmWeatherContextSchema = z.object({
  field_id: uuidSchema,
  generated_at: z.string().datetime({ offset: true }),
  evidence: z.array(weatherEvidenceSchema).max(32),
  current: z.object({
    temperature_c: z.number().finite().nullable(),
    relative_humidity_percent: z.number().min(0).max(100).nullable(),
    wind_kmh: z.number().nonnegative().nullable(),
    precipitation_detected: z.boolean().nullable(),
  }).optional(),
  next_24h: z.object({
    precipitation_probability_percent: z.number().min(0).max(100).nullable(),
    temperature_min_c: z.number().finite().nullable(),
    temperature_max_c: z.number().finite().nullable(),
    wind_max_kmh: z.number().nonnegative().nullable(),
  }).optional(),
  radar: z.object({
    coverage: z.enum(['covered', 'partial', 'outside', 'unavailable']),
    precipitation_detected: z.boolean().nullable(),
    nearest_echo_distance_km: z.number().nonnegative().nullable(),
    representative_dbz: z.number().nullable(),
    observed_at: z.string().datetime({ offset: true }).nullable(),
  }).optional(),
});

export const agronomicAdvisorySchema = z.object({
  id: z.string().min(1).max(160),
  field_id: uuidSchema,
  kind: advisoryKindSchema,
  suitability: agronomicSuitabilitySchema,
  risk_level: agronomicRiskLevelSchema,
  title: z.string().trim().min(1).max(180),
  summary: z.string().trim().min(1).max(600),
  confidence: advisoryConfidenceSchema,
  evidence_ids: z.array(z.string().min(1).max(160)).max(16),
  valid_from: z.string().datetime({ offset: true }).nullable(),
  valid_until: z.string().datetime({ offset: true }).nullable(),
  generated_at: z.string().datetime({ offset: true }),
  rule_version: z.string().min(1).max(80),
  requires_user_judgement: z.boolean().default(true),
});

export type WeatherEvidence = z.infer<typeof weatherEvidenceSchema>;
export type FarmWeatherContext = z.infer<typeof farmWeatherContextSchema>;
export type AgronomicAdvisory = z.infer<typeof agronomicAdvisorySchema>;
export type AgronomicAdvisoryKind = z.infer<typeof advisoryKindSchema>;
