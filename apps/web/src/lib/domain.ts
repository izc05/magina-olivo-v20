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

export type ActivityRecord = {
  id: string;
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

export type FieldRecord = {
  id: string;
  name: string;
  municipality?: string;
  oliveTrees?: number;
  variety?: string;
  waterRegime?: 'Secano' | 'Regadío' | 'Mixto';
  createdAt: string;
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
