export type ActivityType =
  | 'harvest'
  | 'irrigation'
  | 'treatment'
  | 'fertilization'
  | 'pruning'
  | 'labor'
  | 'machinery'
  | 'expense'
  | 'observation';

export type ActivitySource = 'prototype-local' | 'manual' | 'ocr' | 'import';

/**
 * PRODUCT LANGUAGE
 * ----------------
 * The farmer-facing concept is Finca.
 * `fieldId` is kept as the technical/API compatibility identifier while the
 * backend migration is completed. A Finca can contain zero, one or many
 * Parcelas/Recintos and is never defined by Catastro or SIGPAC.
 */
export type FarmOwnership = 'owned' | 'leased' | 'managed' | 'third-party';
export type FarmStatus = 'active' | 'archived';
export type ParcelKind = 'own-boundary' | 'catastro' | 'sigpac' | 'manual';
export type ParcelStatus = 'draft' | 'linked' | 'verified';

export type FarmRecord = {
  id: string;
  name: string;
  municipality?: string;
  province?: string;
  placeId?: string;
  oliveTrees?: number;
  variety?: string;
  waterRegime?: 'Secano' | 'Regadío' | 'Mixto';
  areaHa?: number;
  ownership?: FarmOwnership;
  ownerPartyId?: string;
  status?: FarmStatus;
  notes?: string;
  createdAt: string;
};

/** @deprecated Technical compatibility alias. In product/UI copy use FarmRecord/Finca. */
export type FieldRecord = FarmRecord;

export type ParcelRecord = {
  id: string;
  farmId: string;
  name?: string;
  kind: ParcelKind;
  areaHa?: number;
  geometryGeoJson?: unknown;
  cadastralReference?: string;
  sigpac?: {
    province?: string;
    municipality?: string;
    aggregate?: string;
    zone?: string;
    polygon?: string;
    parcel?: string;
    enclosure?: string;
  };
  oliveTrees?: number;
  variety?: string;
  waterRegime?: 'Secano' | 'Regadío' | 'Mixto';
  status: ParcelStatus;
  createdAt: string;
};

/**
 * PARTY / PERSON MODEL
 * --------------------
 * A Party is any person or organisation with which Mi Campo can have an
 * operational relationship: owner, family member, worker, customer, supplier,
 * cooperative, service company, etc. Roles are contextual and intentionally
 * separate from account/auth roles.
 */
export type PartyKind = 'person' | 'organization';
export type PartyRole =
  | 'owner'
  | 'family'
  | 'worker'
  | 'contractor'
  | 'customer'
  | 'supplier'
  | 'cooperative'
  | 'mill'
  | 'other';

export type PartyRecord = {
  id: string;
  kind: PartyKind;
  displayName: string;
  legalName?: string;
  taxId?: string;
  phone?: string;
  email?: string;
  roles: PartyRole[];
  notes?: string;
  active: boolean;
  createdAt: string;
};

export type CrewRecord = {
  id: string;
  name: string;
  memberPartyIds: string[];
  leaderPartyId?: string;
  defaultRateEur?: number;
  defaultRateUnit?: 'hours' | 'days' | 'jornales' | 'fixed';
  notes?: string;
  active: boolean;
  createdAt: string;
};

export type ResourceOwnership = 'owned' | 'rented' | 'third-party-service';

export type MachineryRecord = {
  id: string;
  name: string;
  category?: string;
  ownership: ResourceOwnership;
  ownerPartyId?: string;
  registrationOrSerial?: string;
  defaultRateEur?: number;
  defaultRateUnit?: 'hours' | 'days' | 'units' | 'fixed';
  notes?: string;
  active: boolean;
  createdAt: string;
};

export type MaterialRecord = {
  id: string;
  name: string;
  category?: string;
  defaultUnit?: string;
  defaultUnitCostEur?: number;
  supplierPartyId?: string;
  notes?: string;
  active: boolean;
  createdAt: string;
};

export type WorkType =
  | 'pruning'
  | 'shredding'
  | 'harvest'
  | 'treatment'
  | 'fertilization'
  | 'irrigation'
  | 'mowing'
  | 'tillage'
  | 'transport'
  | 'manual-work'
  | 'machinery-work'
  | 'other';

export type WorkUnit = 'hours' | 'days' | 'jornales' | 'units' | 'fixed';
export type WorkPaymentStatus = 'not-applicable' | 'pending' | 'partial' | 'paid';
export type WorkScope = 'whole-farm' | 'selected-parcels';

export type WorkParticipant = {
  id: string;
  personId?: string;
  crewId?: string;
  displayName: string;
  role?: string;
  quantity?: number;
  unit?: WorkUnit;
  rateEur?: number;
  costEur?: number;
  paidAmountEur?: number;
  paymentStatus?: WorkPaymentStatus;
};

export type WorkResource = {
  id: string;
  kind: 'machinery' | 'material' | 'service';
  machineryId?: string;
  materialId?: string;
  supplierPartyId?: string;
  name: string;
  quantity?: number;
  unit?: string;
  unitCostEur?: number;
  costEur?: number;
};

export type WorkCommercialContext = {
  customerId?: string;
  quotedAmountEur?: number;
  chargeEur?: number;
  collectedEur?: number;
  paymentStatus: WorkPaymentStatus;
  invoiceReference?: string;
};

/**
 * Trabajo is the operational umbrella entity. Specialist records (riego,
 * tratamiento, abono, etc.) can attach their own agronomic data while sharing
 * destination, people, resources, costs and commercial context here.
 */
export type WorkRecord = {
  id: string;
  farmId: string;
  parcelIds?: string[];
  scope: WorkScope;
  campaign: string;
  type: WorkType;
  occurredOn: string;
  title: string;
  notes?: string;
  performedFor: 'self' | 'third-party';
  participants?: WorkParticipant[];
  resources?: WorkResource[];
  directCostEur?: number;
  commercial?: WorkCommercialContext;
  documentIds?: string[];
  createdAt: string;
};

/**
 * HARVEST / CAMPAIGN MODEL
 * ------------------------
 * A Campaign groups an agricultural season. Deliveries are immutable business
 * events and may allocate olives across multiple farms. Results arrive later
 * and are attached to a delivery without replacing the delivery itself.
 */
export type CampaignStatus = 'planned' | 'active' | 'closed';
export type HarvestSource = 'manual' | 'ocr' | 'import';
export type AllocationStatus = 'provisional' | 'confirmed';

export type CampaignRecord = {
  id: string;
  label: string;
  startsOn?: string;
  endsOn?: string;
  status: CampaignStatus;
  notes?: string;
  createdAt: string;
};

export type HarvestAllocation = {
  id: string;
  farmId: string;
  parcelIds?: string[];
  kg?: number;
  percentage?: number;
  status: AllocationStatus;
};

export type HarvestDeliveryRecord = {
  id: string;
  campaignId: string;
  deliveredAt: string;
  millOrCooperativePartyId?: string;
  millOrCooperativeName?: string;
  ticketNumber?: string;
  totalKg: number;
  source: HarvestSource;
  allocations: HarvestAllocation[];
  documentIds?: string[];
  notes?: string;
  correctionOfDeliveryId?: string;
  createdAt: string;
};

export type HarvestResultRecord = {
  id: string;
  deliveryId: string;
  resultDate: string;
  yieldPercent: number;
  moisturePercent?: number;
  acidityPercent?: number;
  additionalMetrics?: Record<string, number | string>;
  source: HarvestSource;
  documentIds?: string[];
  correctionOfResultId?: string;
  createdAt: string;
};

export type FarmCampaignSummary = {
  farmId: string;
  campaignId: string;
  deliveredKg: number;
  deliveriesCount: number;
  weightedYieldPercent?: number;
  estimatedOilKg?: number;
};

export type ActivityRecord = {
  id: string;
  /** Technical compatibility id: points to the visible Finca. */
  fieldId: string;
  campaign: string;
  type: ActivityType;
  occurredOn: string;
  title: string;
  summary: string;
  costEur?: number;
  followUpOn?: string;
  followUpTime?: string;
  data: Record<string, string>;
  source: ActivitySource;
  createdAt: string;
};

export type ScheduledEventStatus = 'planned' | 'completed' | 'postponed' | 'cancelled';

export type ScheduledEvent = {
  id: string;
  fieldId: string;
  activityId?: string;
  title: string;
  scheduledOn: string;
  scheduledTime?: string;
  status: ScheduledEventStatus;
  source: 'manual' | 'activity-followup' | 'smart';
};

export type CostEntry = {
  id: string;
  fieldId: string;
  campaign: string;
  activityId?: string;
  category: string;
  amountEur: number;
  occurredOn: string;
  source: 'activity' | 'manual';
};

export type LandReferenceSource = 'catastro' | 'sigpac' | 'manual';

export type FieldLandReference = {
  id: string;
  fieldId: string;
  source: LandReferenceSource;
  reference?: string;
  areaHa?: number;
  geometryGeoJson?: unknown;
  status: 'pending' | 'linked' | 'verified';
};

export const recordSlugToActivityType = {
  riego: 'irrigation',
  tratamiento: 'treatment',
  abono: 'fertilization',
  poda: 'pruning',
  jornal: 'labor',
  maquinaria: 'machinery',
  gasto: 'expense',
  observacion: 'observation',
} as const satisfies Record<string, ActivityType>;

export const activityLabels: Record<ActivityType, string> = {
  harvest: 'Cosecha',
  irrigation: 'Riego',
  treatment: 'Tratamiento',
  fertilization: 'Abono',
  pruning: 'Poda',
  labor: 'Jornal',
  machinery: 'Maquinaria',
  expense: 'Gasto',
  observation: 'Observación',
};

export const activitySymbols: Record<ActivityType, string> = {
  harvest: '🫒',
  irrigation: '💧',
  treatment: '🌿',
  fertilization: '🧪',
  pruning: '✂',
  labor: '👷',
  machinery: '🚜',
  expense: '€',
  observation: '📷',
};
