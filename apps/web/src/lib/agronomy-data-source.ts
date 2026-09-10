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
  evidence: {
    source: string;
    municipality?: string;
    precipitationProbabilityPercent?: number;
    windMaxKmh?: number;
    temperatureMinC?: number;
    temperatureMaxC?: number;
    fetchedAt: string;
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
  requires_user_judgement: boolean;
  evidence: {
    source: string;
    municipality: string | null;
    precipitation_probability_percent: number | null;
    wind_max_kmh: number | null;
    temperature_min_c: number | null;
    temperature_max_c: number | null;
    fetched_at: string;
    stale: boolean;
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
    stale: data.evidence.stale,
    evidence: {
      source: data.evidence.source,
      municipality: data.evidence.municipality ?? undefined,
      precipitationProbabilityPercent: data.evidence.precipitation_probability_percent ?? undefined,
      windMaxKmh: data.evidence.wind_max_kmh ?? undefined,
      temperatureMinC: data.evidence.temperature_min_c ?? undefined,
      temperatureMaxC: data.evidence.temperature_max_c ?? undefined,
      fetchedAt: data.evidence.fetched_at,
    },
  };
}
