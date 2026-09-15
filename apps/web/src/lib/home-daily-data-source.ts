import { apiFetch } from '@/lib/api-client';

export type HomeWeatherDay = {
  date: string;
  precipitationProbabilityPercent: number | null;
  temperatureMinC: number | null;
  temperatureMaxC: number | null;
  windMaxKmh: number | null;
};

export type HomeWeatherView = {
  municipalityName: string;
  provider: string;
  days: HomeWeatherDay[];
  stale: boolean;
  cacheStatus?: string;
  fetchedAt?: string;
};

export type HomeActivityItem = {
  id: string;
  occurredAt: string;
  title: string;
  summary?: string;
  domainType: string;
  iconKey?: string;
};

type ApiWeatherResponse = {
  municipality: { name: string };
  forecast: { provider: string; days: HomeWeatherDay[] };
  cache_status?: string;
  fetched_at?: string;
  stale: boolean;
};

type ApiActivityResponse = {
  items: Array<{
    id: string;
    occurred_at: string;
    domain_type: string;
    title: string;
    summary: string | null;
    icon_key: string | null;
  }>;
};

export async function loadHomeWeather(workspaceId: string, fieldId: string): Promise<HomeWeatherView> {
  const data = await apiFetch<ApiWeatherResponse>(`/api/v1/fields/${encodeURIComponent(fieldId)}/weather/daily`, {
    workspaceId,
  });
  return {
    municipalityName: data.municipality.name,
    provider: data.forecast.provider,
    days: data.forecast.days,
    stale: data.stale,
    cacheStatus: data.cache_status,
    fetchedAt: data.fetched_at,
  };
}

export async function loadHomeRecentActivity(
  workspaceId: string,
  fieldId: string,
  limit = 4,
): Promise<HomeActivityItem[]> {
  const data = await apiFetch<ApiActivityResponse>(`/api/v1/fields/${encodeURIComponent(fieldId)}/activity`, {
    workspaceId,
  });
  return data.items.slice(0, Math.max(0, limit)).map((item) => ({
    id: item.id,
    occurredAt: item.occurred_at,
    title: item.title,
    summary: item.summary ?? undefined,
    domainType: item.domain_type,
    iconKey: item.icon_key ?? undefined,
  }));
}

export function weatherProviderLabel(provider?: string) {
  if (!provider) return 'Previsión';
  if (provider.toLowerCase().includes('aemet')) return 'AEMET';
  return provider.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function formatHomeTimestamp(value?: string) {
  if (!value) return undefined;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return undefined;
  return date.toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
