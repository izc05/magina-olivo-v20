import type { FarmRecord } from '@/lib/domain';
import { apiFetch } from '@/lib/api-client';
import { demoFarms } from '@/lib/demo-data';
import { getLocalFarms } from '@/lib/local-prototype-store';

export type FarmListItem = FarmRecord & {
  source: 'api' | 'local' | 'demo';
  statusLabel?: string;
  tone?: 'ok' | 'warn' | 'neutral';
};

export type FarmSummary = {
  farms: number;
  oliveTrees: number;
  areaHa?: number;
};

type ApiField = {
  id: string;
  name: string;
  municipality: string | null;
  province: string | null;
  place_id: string | null;
  tree_count: number | null;
  variety: string | null;
  water_regime: 'secano' | 'regadio' | 'mixto' | null;
  calculated_area_ha: number | string | null;
  tenure_type: string | null;
  status: string;
  created_at: string;
};

type ApiFieldsPayload = { fields: ApiField[] };

function waterRegimeLabel(value: ApiField['water_regime']): FarmRecord['waterRegime'] | undefined {
  if (value === 'regadio') return 'Regadío';
  if (value === 'mixto') return 'Mixto';
  if (value === 'secano') return 'Secano';
  return undefined;
}

function numericArea(value: ApiField['calculated_area_ha']) {
  if (value === null) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function normalizeApiField(field: ApiField): FarmListItem {
  const archived = field.status === 'archived';
  return {
    id: field.id,
    name: field.name,
    municipality: field.municipality ?? undefined,
    province: field.province ?? undefined,
    placeId: field.place_id ?? undefined,
    oliveTrees: field.tree_count ?? undefined,
    variety: field.variety ?? undefined,
    waterRegime: waterRegimeLabel(field.water_regime),
    areaHa: numericArea(field.calculated_area_ha),
    ownership: field.tenure_type === 'leased' ? 'leased' : field.tenure_type === 'managed' ? 'managed' : undefined,
    status: archived ? 'archived' : 'active',
    createdAt: field.created_at,
    source: 'api',
    statusLabel: archived ? 'Archivada' : 'Activa',
    tone: archived ? 'neutral' : 'ok',
  };
}

export function getPreviewFarms(): FarmListItem[] {
  const local = getLocalFarms().map((farm) => ({ ...farm, source: 'local' as const, statusLabel: 'Guardada en este dispositivo', tone: 'neutral' as const }));
  const demo = demoFarms.map((farm) => ({
    id: farm.id,
    name: farm.name,
    municipality: farm.municipality,
    oliveTrees: farm.oliveTrees,
    status: 'active' as const,
    createdAt: '2026-09-01T00:00:00.000Z',
    source: 'demo' as const,
    statusLabel: farm.status,
    tone: farm.tone,
  }));
  return [...local, ...demo];
}

export async function loadWorkspaceFarms(workspaceId: string): Promise<FarmListItem[]> {
  const payload = await apiFetch<ApiFieldsPayload>('/api/v1/fields', { workspaceId });
  return payload.fields.map(normalizeApiField);
}

export function summarizeFarms(farms: FarmListItem[]): FarmSummary {
  const areaValues = farms.map((farm) => farm.areaHa).filter((value): value is number => typeof value === 'number');
  return {
    farms: farms.length,
    oliveTrees: farms.reduce((total, farm) => total + (farm.oliveTrees ?? 0), 0),
    ...(areaValues.length ? { areaHa: areaValues.reduce((total, value) => total + value, 0) } : {}),
  };
}
