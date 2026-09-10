import { apiFetch } from '@/lib/api-client';
import { demoDelivery } from '@/lib/demo-data';
import { getLocalHarvestDeliveries, getLocalHarvestResults, getLocalParcels } from '@/lib/local-prototype-store';

export type FarmActivityView = {
  id: string;
  date: string;
  title: string;
  summary?: string;
  domainType?: string;
  iconKey?: string;
};

export type FarmHarvestDeliveryView = {
  id: string;
  date: string;
  kg: number;
  destination?: string;
  ticketNumber?: string;
  yieldPercent?: number;
  resultDate?: string;
};

export type FarmHarvestSettlementView = {
  id: string;
  date: string;
  counterparty?: string;
  settlementNumber?: string;
  fieldKg: number;
  sharePercent: number;
  netEur: number;
  collectedEur: number;
  pendingEur: number;
  allocationStatus: 'derived_estimate' | string;
};

export type FarmHarvestView = {
  totalKg: number;
  weightedYieldPercent?: number;
  pendingResults: number;
  deliveries: FarmHarvestDeliveryView[];
  accruedEur: number;
  collectedEur: number;
  pendingEur: number;
  settlements: FarmHarvestSettlementView[];
  allocationNotice?: string;
};

export type FarmLandReferenceView = {
  id: string;
  source: 'catastro' | 'sigpac' | 'manual' | string;
  reference?: string;
  areaHa?: number;
  status?: string;
};

export type FarmDataView = {
  geometryStatus?: string;
  geometrySource?: string;
  areaHa?: number;
  references: FarmLandReferenceView[];
  parcelCount: number;
};

export type FarmDocumentView = {
  id: string;
  kind: string;
  title: string;
  createdAt: string;
  domainType?: string;
  relation?: string;
};

export type FarmDetailData = {
  activity: FarmActivityView[];
  harvest: FarmHarvestView;
  data: FarmDataView;
  documents: FarmDocumentView[];
};

type ApiHarvestPayload = {
  total_kg: number;
  weighted_yield_percent: number | null;
  pending_results: number;
  deliveries: Array<{
    delivery_id: string;
    delivery_at: string;
    cooperative_or_mill: string | null;
    ticket_number: string | null;
    kg: number;
    yield_percent: number | null;
    result_date: string | null;
  }>;
};

type ApiHarvestCommercialPayload = {
  accrued_eur: number;
  collected_eur: number;
  pending_eur: number;
  allocation_notice?: string;
  settlements: Array<{
    id: string;
    settled_on: string;
    counterparty_name: string | null;
    settlement_number: string | null;
    field_kg: number;
    share_percent: number;
    net_eur: number;
    collected_eur: number;
    pending_eur: number;
    allocation_status: string;
  }>;
};

type ApiMapPayload = {
  field: {
    calculated_area_ha: number | string | null;
    geometry_source: string | null;
    geometry_status: string | null;
  };
  references: Array<{
    id: string;
    source: string;
    reference: string | null;
    area_ha: number | string | null;
    status: string;
  }>;
};

type ApiDocumentsPayload = {
  documents: Array<{
    id: string;
    kind: string;
    title: string;
    created_at: string;
    domain_type: string | null;
    relation: string | null;
  }>;
};

type ApiActivityPayload = {
  items: Array<{
    id: string;
    occurred_at: string;
    domain_type: string;
    domain_record_id: string;
    title: string;
    summary: string | null;
    icon_key: string | null;
  }>;
};

function finite(value: number | string | null | undefined) {
  if (value === null || value === undefined) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export async function loadApiFarmDetailData(fieldId: string, workspaceId: string): Promise<FarmDetailData> {
  const [activity, harvest, harvestCommercial, map, documents] = await Promise.all([
    apiFetch<ApiActivityPayload>(`/api/v1/fields/${encodeURIComponent(fieldId)}/activity`, { workspaceId }),
    apiFetch<ApiHarvestPayload>(`/api/v1/fields/${encodeURIComponent(fieldId)}/harvest-summary`, { workspaceId }),
    apiFetch<ApiHarvestCommercialPayload>(`/api/v1/fields/${encodeURIComponent(fieldId)}/harvest-commercial-summary`, { workspaceId }),
    apiFetch<ApiMapPayload>(`/api/v1/fields/${encodeURIComponent(fieldId)}/map-context`, { workspaceId }),
    apiFetch<ApiDocumentsPayload>(`/api/v1/fields/${encodeURIComponent(fieldId)}/documents`, { workspaceId }),
  ]);

  return {
    activity: activity.items.map((item) => ({
      id: item.id,
      date: item.occurred_at.slice(0, 10),
      title: item.title,
      summary: item.summary ?? undefined,
      domainType: item.domain_type,
      iconKey: item.icon_key ?? undefined,
    })),
    harvest: {
      totalKg: harvest.total_kg,
      weightedYieldPercent: harvest.weighted_yield_percent ?? undefined,
      pendingResults: harvest.pending_results,
      deliveries: harvest.deliveries.map((item) => ({
        id: item.delivery_id,
        date: item.delivery_at.slice(0, 10),
        kg: item.kg,
        destination: item.cooperative_or_mill ?? undefined,
        ticketNumber: item.ticket_number ?? undefined,
        yieldPercent: item.yield_percent ?? undefined,
        resultDate: item.result_date ?? undefined,
      })),
      accruedEur: harvestCommercial.accrued_eur,
      collectedEur: harvestCommercial.collected_eur,
      pendingEur: harvestCommercial.pending_eur,
      allocationNotice: harvestCommercial.allocation_notice,
      settlements: harvestCommercial.settlements.map((item) => ({
        id: item.id,
        date: item.settled_on,
        counterparty: item.counterparty_name ?? undefined,
        settlementNumber: item.settlement_number ?? undefined,
        fieldKg: item.field_kg,
        sharePercent: item.share_percent,
        netEur: item.net_eur,
        collectedEur: item.collected_eur,
        pendingEur: item.pending_eur,
        allocationStatus: item.allocation_status,
      })),
    },
    data: {
      geometryStatus: map.field.geometry_status ?? undefined,
      geometrySource: map.field.geometry_source ?? undefined,
      areaHa: finite(map.field.calculated_area_ha),
      parcelCount: map.references.length,
      references: map.references.map((item) => ({
        id: item.id,
        source: item.source,
        reference: item.reference ?? undefined,
        areaHa: finite(item.area_ha),
        status: item.status,
      })),
    },
    documents: documents.documents.map((item) => ({
      id: item.id,
      kind: item.kind,
      title: item.title,
      createdAt: item.created_at,
      domainType: item.domain_type ?? undefined,
      relation: item.relation ?? undefined,
    })),
  };
}

export function loadPreviewFarmDetailData(farmId: string, source?: string | null): FarmDetailData {
  const deliveries = getLocalHarvestDeliveries(undefined, farmId);
  const results = getLocalHarvestResults();
  const parcels = getLocalParcels(farmId);

  const deliveryViews: FarmHarvestDeliveryView[] = deliveries.map((delivery) => {
    const allocation = delivery.allocations.find((item) => item.farmId === farmId);
    const kg = allocation?.kg ?? (allocation?.percentage !== undefined ? delivery.totalKg * allocation.percentage / 100 : delivery.allocations.length === 1 ? delivery.totalKg : 0);
    const result = results.filter((item) => item.deliveryId === delivery.id).sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
    return {
      id: delivery.id,
      date: delivery.deliveredAt.slice(0, 10),
      kg,
      destination: delivery.millOrCooperativeName,
      ticketNumber: delivery.ticketNumber,
      yieldPercent: result?.yieldPercent,
      resultDate: result?.resultDate,
    };
  });

  if (source === 'demo' && farmId === 'las-cenillas' && deliveryViews.length === 0) {
    deliveryViews.push({
      id: 'demo-delivery',
      date: '2026-12-12',
      kg: demoDelivery.kilograms,
      destination: demoDelivery.cooperative,
      ticketNumber: demoDelivery.ticketNumber,
    });
  }

  const totalKg = deliveryViews.reduce((sum, item) => sum + item.kg, 0);
  const withResult = deliveryViews.filter((item) => item.yieldPercent !== undefined && item.kg > 0);
  const resultKg = withResult.reduce((sum, item) => sum + item.kg, 0);
  const weightedYieldPercent = resultKg > 0 ? withResult.reduce((sum, item) => sum + item.kg * (item.yieldPercent ?? 0), 0) / resultKg : undefined;

  return {
    activity: [],
    harvest: {
      totalKg,
      weightedYieldPercent,
      pendingResults: deliveryViews.filter((item) => item.yieldPercent === undefined).length,
      deliveries: deliveryViews.sort((a, b) => b.date.localeCompare(a.date)),
      accruedEur: 0,
      collectedEur: 0,
      pendingEur: 0,
      settlements: [],
    },
    data: {
      parcelCount: parcels.length,
      references: parcels.map((parcel) => ({
        id: parcel.id,
        source: parcel.kind,
        reference: parcel.cadastralReference,
        areaHa: parcel.areaHa,
        status: parcel.status,
      })),
    },
    documents: [],
  };
}

export function emptyFarmDetailData(): FarmDetailData {
  return {
    activity: [],
    harvest: { totalKg: 0, pendingResults: 0, deliveries: [], accruedEur: 0, collectedEur: 0, pendingEur: 0, settlements: [] },
    data: { parcelCount: 0, references: [] },
    documents: [],
  };
}
