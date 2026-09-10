import { apiFetch } from '@/lib/api-client';

export type AgronomyAdvisoryView = {
  fieldId: string;
  date: string;
  task: 'treatment' | 'irrigation' | 'pruning' | 'harvest' | 'work';
  suitability: 'good' | 'caution' | 'avoid' | 'unknown';
  riskLevel: 'none' | 'low' | 'medium' | 'high' | 'unknown';
  title: string;
  summary: string;
  confidence: 'low' | 'medium' | 'high';
  ruleVersion: string;
  requiresUserJudgement: boolean;
  stale: boolean;
  radarElevated: boolean;
  forecastEvidence: {
    source: string;
    municipality?: string;
    precipitationProbabilityPercent?: number;
    windMaxKmh?: number;
    temperatureMinC?: number;
    temperatureMaxC?: number;
    fetchedAt: string;
  };
  radarEvidence?: {
    observedAt: string;
    coverageStatus: 'covered' | 'partial' | 'outside' | 'unavailable';
    precipitationDetected?: boolean;
    nearestEchoDistanceKm?: number;
    reflectivityDbzMax?: number;
    qualityFlags: string[];
    fresh: boolean;
    ageMinutes?: number;
    summary: string;
  };
};

type ApiAgronomyAdvisory = {
  field_id: string;
  date: string;
  task: AgronomyAdvisoryView['task'];
  suitability: AgronomyAdvisoryView['suitability'];
  risk_level: AgronomyAdvisoryView['riskLevel'];
  title: string;
  summary: string;
  confidence: AgronomyAdvisoryView['confidence'];
  rule_version: string;
  radar_elevated?: boolean;
  requires_user_judgement: boolean;
  evidence: {
    forecast: {
      source: string;
      municipality: string | null;
      precipitation_probability_percent: number | null;
      wind_max_kmh: number | null;
      temperature_min_c: number | null;
      temperature_max_c: number | null;
      fetched_at: string;
      stale: boolean;
    };
    radar: null | {
      observed_at: string;
      coverage_status: 'covered' | 'partial' | 'outside' | 'unavailable';
      precipitation_detected: boolean | null;
      nearest_echo_distance_km: number | null;
      reflectivity_dbz_max: number | null;
      quality_flags: string[];
      fresh: boolean;
      age_minutes: number | null;
      summary: string;
    };
  };
};

export function agendaDomainToAgronomyTask(domainType?: string): AgronomyAdvisoryView['task'] {
  if (domainType === 'treatment') return 'treatment';
  if (domainType === 'irrigation') return 'irrigation';
  if (domainType === 'pruning') return 'pruning';
  if (domainType === 'harvest' || domainType === 'harvest_delivery') return 'harvest';
  return 'work';
}

export async function loadAgronomyAdvisory(input: {
  workspaceId: string;
  fieldId: string;
  date: string;
  task: AgronomyAdvisoryView['task'];
}): Promise<AgronomyAdvisoryView> {
  const params = new URLSearchParams({ date: input.date, task: input.task });
  const data = await apiFetch<ApiAgronomyAdvisory>(`/api/v1/fields/${encodeURIComponent(input.fieldId)}/agronomy/advisory?${params.toString()}`, { workspaceId: input.workspaceId });
  const forecast = data.evidence.forecast;
  const radar = data.evidence.radar;
  return {
    fieldId: data.field_id,
    date: data.date,
    task: data.task,
    suitability: data.suitability,
    riskLevel: data.risk_level,
    title: data.title,
    summary: data.summary,
    confidence: data.confidence,
    ruleVersion: data.rule_version,
    requiresUserJudgement: data.requires_user_judgement,
    stale: forecast.stale,
    radarElevated: Boolean(data.radar_elevated),
    forecastEvidence: {
      source: forecast.source,
      municipality: forecast.municipality ?? undefined,
      precipitationProbabilityPercent: forecast.precipitation_probability_percent ?? undefined,
      windMaxKmh: forecast.wind_max_kmh ?? undefined,
      temperatureMinC: forecast.temperature_min_c ?? undefined,
      temperatureMaxC: forecast.temperature_max_c ?? undefined,
      fetchedAt: forecast.fetched_at,
    },
    radarEvidence: radar ? {
      observedAt: radar.observed_at,
      coverageStatus: radar.coverage_status,
      precipitationDetected: radar.precipitation_detected ?? undefined,
      nearestEchoDistanceKm: radar.nearest_echo_distance_km ?? undefined,
      reflectivityDbzMax: radar.reflectivity_dbz_max ?? undefined,
      qualityFlags: radar.quality_flags,
      fresh: radar.fresh,
      ageMinutes: radar.age_minutes ?? undefined,
      summary: radar.summary,
    } : undefined,
  };
}
