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
  displayName: string;
  role?: string;
  quantity?: number;
  unit?: WorkUnit;
  rateEur?: number;
  costEur?: number;
};

export type WorkResource = {
  id: string;
  kind: 'machinery' | 'material' | 'service';
  name: string;
  quantity?: number;
  unit?: string;
  unitCostEur?: number;
  costEur?: number;
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
  customerId?: string;
  performedFor: 'self' | 'third-party';
  participants?: WorkParticipant[];
  resources?: WorkResource[];
  directCostEur?: number;
  chargeEur?: number;
  paymentStatus?: WorkPaymentStatus;
  documentIds?: string[];
  createdAt: string;
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
