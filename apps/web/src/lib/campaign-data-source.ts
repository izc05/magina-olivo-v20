import { apiFetch } from '@/lib/api-client';
import { getLocalCampaigns, getLocalHarvestDeliveries, getLocalHarvestResults } from '@/lib/local-prototype-store';
import { getLocalEconomicEvents } from '@/lib/local-economics-store';
import { getPreviewFarms } from '@/lib/farm-data-source';

export type CampaignListItem = {
  id: string;
  name: string;
  startDate: string;
  endDate?: string;
  status: 'planned' | 'active' | 'closed';
};

export type CampaignFieldSummary = {
  fieldId: string;
  fieldName: string;
  deliveredKg: number;
  totalCostEur: number;
  accruedIncomeEur: number;
  collectedIncomeEur: number;
  pendingCollectionEur: number;
  accruedMarginEur: number;
  cashMarginEur: number;
  costPerDeliveredKgEur?: number;
};

export type CampaignSummaryView = {
  campaign: CampaignListItem;
  deliveredKg: number;
  weightedYieldPercent?: number;
  kgWithResult: number;
  pendingResultKg: number;
  totalCostEur: number;
  accruedIncomeEur: number;
  collectedIncomeEur: number;
  pendingCollectionEur: number;
  accruedMarginEur: number;
  cashMarginEur: number;
  costPerDeliveredKgEur?: number;
  deliveryCount: number;
  settlementCount: number;
  fieldCount: number;
  fields: CampaignFieldSummary[];
  attributionStatus: string;
};

type ApiCampaigns = {
  campaigns: Array<{
    id: string;
    name: string;
    start_date: string;
    end_date: string | null;
    status: 'planned' | 'active' | 'closed';
  }>;
};

type ApiSummary = {
  campaign: ApiCampaigns['campaigns'][number];
  delivered_kg: number;
  weighted_yield_percent: number | null;
  kg_with_result: number;
  pending_result_kg: number;
  total_cost_eur: number;
  accrued_income_eur: number;
  collected_income_eur: number;
  pending_collection_eur: number;
  accrued_margin_eur: number;
  cash_margin_eur: number;
  cost_per_delivered_kg_eur: number | null;
  delivery_count: number;
  settlement_count: number;
  field_count: number;
  fields: Array<{
    field_id: string;
    field_name: string;
    delivered_kg: number;
    total_cost_eur: number;
    accrued_income_eur: number;
    collected_income_eur: number;
    pending_collection_eur: number;
    accrued_margin_eur: number;
    cash_margin_eur: number;
    cost_per_delivered_kg_eur: number | null;
  }>;
  attribution_status: string;
};

function mapCampaign(item: ApiCampaigns['campaigns'][number]): CampaignListItem {
  return {
    id: item.id,
    name: item.name,
    startDate: item.start_date,
    endDate: item.end_date ?? undefined,
    status: item.status,
  };
}

export async function loadApiCampaigns(workspaceId: string) {
  const data = await apiFetch<ApiCampaigns>('/api/v1/campaigns', { workspaceId });
  return data.campaigns.map(mapCampaign);
}

export async function loadApiCampaignSummary(campaignId: string, workspaceId: string): Promise<CampaignSummaryView> {
  const data = await apiFetch<ApiSummary>(`/api/v1/campaigns/${encodeURIComponent(campaignId)}/summary`, { workspaceId });
  return {
    campaign: mapCampaign(data.campaign),
    deliveredKg: data.delivered_kg,
    weightedYieldPercent: data.weighted_yield_percent ?? undefined,
    kgWithResult: data.kg_with_result,
    pendingResultKg: data.pending_result_kg,
    totalCostEur: data.total_cost_eur,
    accruedIncomeEur: data.accrued_income_eur,
    collectedIncomeEur: data.collected_income_eur,
    pendingCollectionEur: data.pending_collection_eur,
    accruedMarginEur: data.accrued_margin_eur,
    cashMarginEur: data.cash_margin_eur,
    costPerDeliveredKgEur: data.cost_per_delivered_kg_eur ?? undefined,
    deliveryCount: data.delivery_count,
    settlementCount: data.settlement_count,
    fieldCount: data.field_count,
    fields: data.fields.map((field) => ({
      fieldId: field.field_id,
      fieldName: field.field_name,
      deliveredKg: field.delivered_kg,
      totalCostEur: field.total_cost_eur,
      accruedIncomeEur: field.accrued_income_eur,
      collectedIncomeEur: field.collected_income_eur,
      pendingCollectionEur: field.pending_collection_eur,
      accruedMarginEur: field.accrued_margin_eur,
      cashMarginEur: field.cash_margin_eur,
      costPerDeliveredKgEur: field.cost_per_delivered_kg_eur ?? undefined,
    })),
    attributionStatus: data.attribution_status,
  };
}

export function getPreviewCampaigns(): CampaignListItem[] {
  return getLocalCampaigns().map((item) => ({
    id: item.id,
    name: item.label,
    startDate: item.startsOn ?? item.createdAt.slice(0, 10),
    endDate: item.endsOn,
    status: item.status,
  }));
}

export function getPreviewCampaignSummary(campaignId: string): CampaignSummaryView | null {
  const campaign = getPreviewCampaigns().find((item) => item.id === campaignId);
  if (!campaign) return null;
  const deliveries = getLocalHarvestDeliveries(campaignId);
  const results = getLocalHarvestResults();
  const farms = getPreviewFarms();
  const costs = getLocalEconomicEvents({ campaignId }).filter((item) => item.direction === 'outflow');

  const byFarm = new Map<string, CampaignFieldSummary>();
  for (const farm of farms) {
    byFarm.set(farm.id, {
      fieldId: farm.id,
      fieldName: farm.name,
      deliveredKg: 0,
      totalCostEur: 0,
      accruedIncomeEur: 0,
      collectedIncomeEur: 0,
      pendingCollectionEur: 0,
      accruedMarginEur: 0,
      cashMarginEur: 0,
    });
  }

  let deliveredKg = 0;
  let kgWithResult = 0;
  let weightedNumerator = 0;
  for (const delivery of deliveries) {
    const result = results.find((item) => item.deliveryId === delivery.id);
    for (const allocation of delivery.allocations) {
      const kg = allocation.kg ?? (allocation.percentage !== undefined ? delivery.totalKg * allocation.percentage / 100 : delivery.allocations.length === 1 ? delivery.totalKg : 0);
      deliveredKg += kg;
      const target = byFarm.get(allocation.farmId);
      if (target) target.deliveredKg += kg;
      if (result?.yieldPercent !== undefined) {
        kgWithResult += kg;
        weightedNumerator += kg * result.yieldPercent;
      }
    }
  }

  for (const cost of costs) {
    if (!cost.farmId) continue;
    const target = byFarm.get(cost.farmId);
    if (target) target.totalCostEur += cost.amountEur;
  }

  const fields = Array.from(byFarm.values()).filter((item) => item.deliveredKg > 0 || item.totalCostEur > 0).map((item) => ({
    ...item,
    accruedMarginEur: item.accruedIncomeEur - item.totalCostEur,
    cashMarginEur: item.collectedIncomeEur - item.totalCostEur,
    costPerDeliveredKgEur: item.deliveredKg > 0 ? item.totalCostEur / item.deliveredKg : undefined,
  }));
  const totalCostEur = fields.reduce((sum, item) => sum + item.totalCostEur, 0);

  return {
    campaign,
    deliveredKg,
    weightedYieldPercent: kgWithResult > 0 ? weightedNumerator / kgWithResult : undefined,
    kgWithResult,
    pendingResultKg: Math.max(deliveredKg - kgWithResult, 0),
    totalCostEur,
    accruedIncomeEur: 0,
    collectedIncomeEur: 0,
    pendingCollectionEur: 0,
    accruedMarginEur: -totalCostEur,
    cashMarginEur: -totalCostEur,
    costPerDeliveredKgEur: deliveredKg > 0 ? totalCostEur / deliveredKg : undefined,
    deliveryCount: deliveries.length,
    settlementCount: 0,
    fieldCount: fields.length,
    fields,
    attributionStatus: 'preview_without_harvest_settlement_store',
  };
}
