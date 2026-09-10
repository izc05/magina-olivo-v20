import type {
  CostAllocation,
  EconomicEventRecord,
  SettlementRecord,
} from '@/lib/economics-domain';

const ECONOMIC_EVENT_KEY = 'magina:v20:economic-events';
const SETTLEMENT_KEY = 'magina:v20:settlements';
const COST_ALLOCATION_KEY = 'magina:v20:cost-allocations';

function readArray<T>(key: string): T[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed as T[] : [];
  } catch {
    return [];
  }
}

function writeArray<T>(key: string, value: T[]) {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(key, JSON.stringify(value));
  window.dispatchEvent(new CustomEvent('magina:prototype-data-changed', { detail: { key } }));
}

export function getLocalEconomicEvents(filters?: { farmId?: string; campaignId?: string }) {
  return readArray<EconomicEventRecord>(ECONOMIC_EVENT_KEY).filter((item) => {
    if (filters?.farmId && item.farmId !== filters.farmId) return false;
    if (filters?.campaignId && item.campaignId !== filters.campaignId) return false;
    return true;
  });
}

export function saveLocalEconomicEvent(event: EconomicEventRecord) {
  const current = readArray<EconomicEventRecord>(ECONOMIC_EVENT_KEY);
  writeArray(ECONOMIC_EVENT_KEY, [event, ...current.filter((item) => item.id !== event.id)]);
  return event;
}

export function getLocalSettlements() {
  return readArray<SettlementRecord>(SETTLEMENT_KEY);
}

export function saveLocalSettlement(settlement: SettlementRecord) {
  const current = readArray<SettlementRecord>(SETTLEMENT_KEY);
  writeArray(SETTLEMENT_KEY, [settlement, ...current.filter((item) => item.id !== settlement.id)]);
  return settlement;
}

export function getLocalCostAllocations(economicEventId?: string) {
  const items = readArray<CostAllocation>(COST_ALLOCATION_KEY);
  return economicEventId ? items.filter((item) => item.economicEventId === economicEventId) : items;
}

export function saveLocalCostAllocation(allocation: CostAllocation) {
  const current = readArray<CostAllocation>(COST_ALLOCATION_KEY);
  writeArray(COST_ALLOCATION_KEY, [allocation, ...current.filter((item) => item.id !== allocation.id)]);
  return allocation;
}

export function clearLocalEconomicsData() {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(ECONOMIC_EVENT_KEY);
  window.localStorage.removeItem(SETTLEMENT_KEY);
  window.localStorage.removeItem(COST_ALLOCATION_KEY);
  window.dispatchEvent(new CustomEvent('magina:prototype-data-changed', { detail: { key: 'economics' } }));
}

export const economicsStoreKeys = {
  economicEvents: ECONOMIC_EVENT_KEY,
  settlements: SETTLEMENT_KEY,
  costAllocations: COST_ALLOCATION_KEY,
} as const;
