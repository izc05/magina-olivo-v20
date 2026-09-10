import { apiFetch } from '@/lib/api-client';

export type AttentionItem = {
  id: string;
  fieldId: string;
  fieldName: string;
  title: string;
  scheduledAt: string;
  sourceDomainType?: string;
  overdue: boolean;
  advisory?: {
    suitability: 'good' | 'caution' | 'avoid' | 'unknown';
    riskLevel: 'none' | 'low' | 'medium' | 'high' | 'unknown';
    summary: string;
    stale: boolean;
    radarElevated: boolean;
    forecast: {
      source: string;
      precipitationProbabilityPercent?: number;
      windMaxKmh?: number;
    };
    radar?: {
      observedAt: string;
      fresh: boolean;
      ageMinutes?: number;
      precipitationDetected?: boolean;
      nearestEchoDistanceKm?: number;
      reflectivityDbzMax?: number;
    };
  };
};

export type AttentionSummary = {
  generatedAt: string;
  counts: { total: number; important: number };
  items: AttentionItem[];
};

type ApiAttentionSummary = {
  generated_at: string;
  counts: { total: number; important: number };
  items: Array<{
    id: string;
    field_id: string;
    field_name: string;
    title: string;
    scheduled_at: string;
    source_domain_type: string | null;
    overdue: boolean;
    advisory: null | {
      suitability: AttentionItem['advisory'] extends infer A ? A extends { suitability: infer S } ? S : never : never;
      risk_level: 'none' | 'low' | 'medium' | 'high' | 'unknown';
      summary: string;
      stale: boolean;
      radar_elevated: boolean;
      forecast: {
        source: string;
        precipitation_probability_percent: number | null;
        wind_max_kmh: number | null;
      };
      radar: null | {
        observed_at: string;
        fresh: boolean;
        age_minutes: number | null;
        precipitation_detected: boolean | null;
        nearest_echo_distance_km: number | null;
        reflectivity_dbz_max: number | null;
      };
    };
  }>;
};

export async function loadAttentionSummary(input: { workspaceId: string; fieldId?: string; limit?: number }): Promise<AttentionSummary> {
  const params = new URLSearchParams();
  if (input.fieldId) params.set('fieldId', input.fieldId);
  if (input.limit) params.set('limit', String(input.limit));
  const suffix = params.size ? `?${params.toString()}` : '';
  const data = await apiFetch<ApiAttentionSummary>(`/api/v1/attention${suffix}`, { workspaceId: input.workspaceId });

  return {
    generatedAt: data.generated_at,
    counts: data.counts,
    items: data.items.map((item) => ({
      id: item.id,
      fieldId: item.field_id,
      fieldName: item.field_name,
      title: item.title,
      scheduledAt: item.scheduled_at,
      sourceDomainType: item.source_domain_type ?? undefined,
      overdue: item.overdue,
      advisory: item.advisory ? {
        suitability: item.advisory.suitability,
        riskLevel: item.advisory.risk_level,
        summary: item.advisory.summary,
        stale: item.advisory.stale,
        radarElevated: item.advisory.radar_elevated,
        forecast: {
          source: item.advisory.forecast.source,
          precipitationProbabilityPercent: item.advisory.forecast.precipitation_probability_percent ?? undefined,
          windMaxKmh: item.advisory.forecast.wind_max_kmh ?? undefined,
        },
        radar: item.advisory.radar ? {
          observedAt: item.advisory.radar.observed_at,
          fresh: item.advisory.radar.fresh,
          ageMinutes: item.advisory.radar.age_minutes ?? undefined,
          precipitationDetected: item.advisory.radar.precipitation_detected ?? undefined,
          nearestEchoDistanceKm: item.advisory.radar.nearest_echo_distance_km ?? undefined,
          reflectivityDbzMax: item.advisory.radar.reflectivity_dbz_max ?? undefined,
        } : undefined,
      } : undefined,
    })),
  };
}
