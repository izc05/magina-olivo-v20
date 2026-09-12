export type MoneyDirection = 'outflow' | 'inflow';
export type SettlementStatus = 'pending' | 'partial' | 'settled' | 'cancelled';
export type EconomicSource =
  | 'work'
  | 'harvest'
  | 'manual'
  | 'document'
  | 'import'
  | 'system';

export type CostCategory =
  | 'labor'
  | 'machinery'
  | 'treatment'
  | 'fertilization'
  | 'water'
  | 'electricity'
  | 'fuel'
  | 'external-service'
  | 'material'
  | 'rent'
  | 'insurance'
  | 'tax'
  | 'maintenance'
  | 'transport'
  | 'other';

export type IncomeCategory =
  | 'olive-sale'
  | 'oil-sale'
  | 'subsidy'
  | 'third-party-work'
  | 'compensation'
  | 'other';

/**
 * EconomicEvent expresses the economic meaning of something that happened.
 * It is NOT the same as cash movement. Example: a worker can generate a cost
 * today while the payment is made next week.
 */
export type EconomicEventRecord = {
  id: string;
  farmId?: string;
  campaignId?: string;
  workId?: string;
  deliveryId?: string;
  partyId?: string;
  occurredOn: string;
  direction: MoneyDirection;
  category: CostCategory | IncomeCategory;
  concept: string;
  amountEur: number;
  source: EconomicSource;
  documentIds?: string[];
  notes?: string;
  createdAt: string;
};

/**
 * Settlement records actual money paid or collected. It can settle one or
 * several economic events partially or completely.
 */
export type SettlementAllocation = {
  economicEventId: string;
  amountEur: number;
};

export type SettlementRecord = {
  id: string;
  direction: MoneyDirection;
  settledOn: string;
  amountEur: number;
  partyId?: string;
  method?: 'cash' | 'bank' | 'card' | 'bizum' | 'offset' | 'other';
  status: SettlementStatus;
  allocations?: SettlementAllocation[];
  reference?: string;
  documentIds?: string[];
  notes?: string;
  createdAt: string;
};

/**
 * Optional allocation for shared costs. It avoids forcing the farmer to split
 * invoices manually when one purchase/service affects several farms.
 */
export type CostAllocation = {
  id: string;
  economicEventId: string;
  farmId: string;
  campaignId?: string;
  amountEur?: number;
  percentage?: number;
  basis: 'manual' | 'area' | 'trees' | 'hours' | 'kg' | 'equal';
};

export type FarmEconomicsSummary = {
  farmId: string;
  campaignId?: string;
  totalCostEur: number;
  totalIncomeEur: number;
  pendingPaymentsEur: number;
  pendingCollectionsEur: number;
  deliveredKg?: number;
  estimatedOilKg?: number;
  costPerOliveKgEur?: number;
  costPerEstimatedOilKgEur?: number;
  marginEur: number;
};

export type CampaignEconomicsSummary = {
  campaignId: string;
  totalCostEur: number;
  totalIncomeEur: number;
  marginEur: number;
  deliveredKg: number;
  estimatedOilKg?: number;
  costPerOliveKgEur?: number;
  costPerEstimatedOilKgEur?: number;
};

export function economicMargin(totalIncomeEur: number, totalCostEur: number) {
  return totalIncomeEur - totalCostEur;
}

export function unitCost(totalCostEur: number, units?: number) {
  if (!units || units <= 0) return undefined;
  return totalCostEur / units;
}
