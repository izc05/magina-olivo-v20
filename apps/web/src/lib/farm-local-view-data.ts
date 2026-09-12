import type { WorkRecord, HarvestDeliveryRecord, HarvestResultRecord } from '@/lib/domain';
import type { EconomicEventRecord } from '@/lib/economics-domain';
import {
  getLocalHarvestDeliveries,
  getLocalHarvestResults,
  getLocalWorks,
} from '@/lib/local-prototype-store';
import { getLocalEconomicEvents } from '@/lib/local-economics-store';

export type FarmActivityItem = {
  id: string;
  date: string;
  title: string;
  summary?: string;
  kind: 'work' | 'harvest' | 'economy';
  amountEur?: number;
};

export type FarmDerivedView = {
  workCount: number;
  deliveryCount: number;
  deliveredKg: number;
  weightedYieldPercent?: number;
  estimatedOilKg?: number;
  totalCostEur: number;
  totalIncomeEur: number;
  marginEur: number;
  recentActivity: FarmActivityItem[];
};

function deliveryKgForFarm(delivery: HarvestDeliveryRecord, farmId: string) {
  const allocation = delivery.allocations.find((item) => item.farmId === farmId);
  if (!allocation) return 0;
  if (typeof allocation.kg === 'number') return allocation.kg;
  if (typeof allocation.percentage === 'number') return delivery.totalKg * (allocation.percentage / 100);
  return delivery.allocations.length === 1 ? delivery.totalKg : 0;
}

function resultForDelivery(results: HarvestResultRecord[], deliveryId: string) {
  return results
    .filter((item) => item.deliveryId === deliveryId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
}

function workSummary(work: WorkRecord) {
  const parts = [
    work.participants?.length ? `${work.participants.length} participante${work.participants.length === 1 ? '' : 's'}` : undefined,
    work.resources?.length ? `${work.resources.length} recurso${work.resources.length === 1 ? '' : 's'}` : undefined,
    work.performedFor === 'third-party' ? 'Trabajo para tercero' : undefined,
  ].filter(Boolean);
  return parts.join(' · ') || work.notes;
}

function economyActivity(event: EconomicEventRecord): FarmActivityItem {
  return {
    id: `economy:${event.id}`,
    date: event.occurredOn,
    title: event.concept,
    summary: event.direction === 'outflow' ? 'Coste / gasto' : 'Ingreso',
    kind: 'economy',
    amountEur: event.direction === 'outflow' ? -event.amountEur : event.amountEur,
  };
}

export function getLocalFarmDerivedView(farmId: string): FarmDerivedView {
  const works = getLocalWorks(farmId);
  const deliveries = getLocalHarvestDeliveries(undefined, farmId);
  const allResults = getLocalHarvestResults();
  const economics = getLocalEconomicEvents({ farmId });

  let deliveredKg = 0;
  let weightedYieldNumerator = 0;
  let weightedYieldKg = 0;

  for (const delivery of deliveries) {
    const kg = deliveryKgForFarm(delivery, farmId);
    deliveredKg += kg;
    const result = resultForDelivery(allResults, delivery.id);
    if (result && kg > 0) {
      weightedYieldNumerator += kg * result.yieldPercent;
      weightedYieldKg += kg;
    }
  }

  const weightedYieldPercent = weightedYieldKg > 0 ? weightedYieldNumerator / weightedYieldKg : undefined;
  const estimatedOilKg = weightedYieldPercent !== undefined ? deliveredKg * (weightedYieldPercent / 100) : undefined;
  const totalCostEur = economics.filter((item) => item.direction === 'outflow').reduce((sum, item) => sum + item.amountEur, 0);
  const totalIncomeEur = economics.filter((item) => item.direction === 'inflow').reduce((sum, item) => sum + item.amountEur, 0);

  const activity: FarmActivityItem[] = [
    ...works.map((work) => ({
      id: `work:${work.id}`,
      date: work.occurredOn,
      title: work.title,
      summary: workSummary(work),
      kind: 'work' as const,
      amountEur: work.directCostEur ? -work.directCostEur : undefined,
    })),
    ...deliveries.map((delivery) => ({
      id: `harvest:${delivery.id}`,
      date: delivery.deliveredAt.slice(0, 10),
      title: 'Entrega de aceituna',
      summary: `${Math.round(deliveryKgForFarm(delivery, farmId)).toLocaleString('es-ES')} kg${delivery.millOrCooperativeName ? ` · ${delivery.millOrCooperativeName}` : ''}`,
      kind: 'harvest' as const,
    })),
    ...economics.map(economyActivity),
  ].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 8);

  return {
    workCount: works.length,
    deliveryCount: deliveries.length,
    deliveredKg,
    weightedYieldPercent,
    estimatedOilKg,
    totalCostEur,
    totalIncomeEur,
    marginEur: totalIncomeEur - totalCostEur,
    recentActivity: activity,
  };
}
