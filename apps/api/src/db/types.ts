import type { ColumnType, Generated } from 'kysely';

export type Timestamp = ColumnType<Date, Date | string, Date | string>;
export type DateColumn = ColumnType<Date, Date | string, Date | string>;

export interface WorkspaceTable {
  id: Generated<string>;
  name: string;
  type: 'family' | 'professional' | 'organization';
  created_at: Generated<Timestamp>;
  updated_at: Generated<Timestamp>;
}

export interface FieldTable {
  id: string;
  workspace_id: string;
  client_operation_id: string;
  name: string;
  description: string | null;
  municipality: string | null;
  province: string | null;
  calculated_area_ha: number | null;
  tree_count: number | null;
  crop: string;
  variety: string | null;
  water_regime: 'secano' | 'regadio' | 'mixto' | null;
  planting_year: number | null;
  tenure_type: string | null;
  status: 'active' | 'archived';
  created_at: Generated<Timestamp>;
  updated_at: Generated<Timestamp>;
}

export interface CampaignTable {
  id: Generated<string>;
  workspace_id: string;
  name: string;
  start_date: DateColumn;
  end_date: ColumnType<Date | null, Date | string | null, Date | string | null>;
  status: 'planned' | 'active' | 'closed';
  created_at: Generated<Timestamp>;
}

interface DomainRecordBase {
  id: string;
  workspace_id: string;
  field_id: string;
  campaign_id: string | null;
  client_operation_id: string;
  created_by: string;
  created_at: Generated<Timestamp>;
  updated_at: Generated<Timestamp>;
}

export interface IrrigationRecordTable extends DomainRecordBase {
  occurred_at: Timestamp;
  duration_hours: number | null;
  water_m3: number | null;
  cost_eur: number | null;
  notes: string | null;
}

export interface TreatmentRecordTable extends DomainRecordBase {
  occurred_at: Timestamp;
  reason: string;
  product_name: string;
  dose: string | null;
  quantity: string | null;
  applicator: string | null;
  equipment: string | null;
  cost_eur: number | null;
  notes: string | null;
}

export interface FertilizationRecordTable extends DomainRecordBase {
  occurred_at: Timestamp;
  product_name: string;
  quantity_kg: number | null;
  application_method: string | null;
  composition: string | null;
  cost_eur: number | null;
  supplier: string | null;
  notes: string | null;
}

export interface PruningRecordTable extends DomainRecordBase {
  occurred_at: Timestamp;
  pruning_type: string;
  workers: number | null;
  hours: number | null;
  cost_eur: number | null;
  notes: string | null;
}

export interface ExpenseRecordTable extends DomainRecordBase {
  occurred_on: DateColumn;
  category: string;
  concept: string;
  amount_eur: number;
  notes: string | null;
}

export interface CostLedgerProjectionTable {
  id: Generated<string>;
  workspace_id: string;
  field_id: string;
  campaign_id: string | null;
  occurred_on: DateColumn;
  domain_type: string;
  domain_record_id: string;
  category: string;
  amount_eur: number;
  created_at: Generated<Timestamp>;
}

export interface FarmTimelineProjectionTable {
  id: Generated<string>;
  workspace_id: string;
  field_id: string;
  occurred_at: Timestamp;
  domain_type: string;
  domain_record_id: string;
  title: string;
  summary: string | null;
  icon_key: string | null;
  created_at: Generated<Timestamp>;
}

export interface ScheduledEventTable {
  id: string;
  workspace_id: string;
  field_id: string | null;
  source_domain_type: string | null;
  source_domain_record_id: string | null;
  title: string;
  scheduled_at: Timestamp;
  status: 'planned' | 'completed' | 'postponed' | 'cancelled';
  source: 'manual' | 'domain_followup' | 'smart';
  created_at: Generated<Timestamp>;
  updated_at: Generated<Timestamp>;
}

export interface Database {
  workspaces: WorkspaceTable;
  fields: FieldTable;
  campaigns: CampaignTable;
  irrigation_records: IrrigationRecordTable;
  treatment_records: TreatmentRecordTable;
  fertilization_records: FertilizationRecordTable;
  pruning_records: PruningRecordTable;
  expense_records: ExpenseRecordTable;
  cost_ledger_projection: CostLedgerProjectionTable;
  farm_timeline_projection: FarmTimelineProjectionTable;
  scheduled_events: ScheduledEventTable;
}
