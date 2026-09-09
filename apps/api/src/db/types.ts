import type { ColumnType, Generated } from 'kysely';

export type Timestamp = ColumnType<Date, Date | string, Date | string>;
export type NullableTimestamp = ColumnType<Date | null, Date | string | null, Date | string | null>;

export interface WorkspaceTable {
  id: string;
  name: string;
  type: 'family' | 'professional' | 'organization';
  created_at: Generated<Timestamp>;
  updated_at: Generated<Timestamp>;
}

export interface FieldTable {
  id: string;
  workspace_id: string;
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
  id: string;
  workspace_id: string;
  name: string;
  start_date: ColumnType<Date, Date | string, Date | string>;
  end_date: ColumnType<Date | null, Date | string | null, Date | string | null>;
  status: 'planned' | 'active' | 'closed';
  created_at: Generated<Timestamp>;
}

export interface IrrigationRecordTable {
  id: string;
  workspace_id: string;
  field_id: string;
  campaign_id: string | null;
  occurred_at: Timestamp;
  duration_hours: number | null;
  water_m3: number | null;
  cost_eur: number | null;
  notes: string | null;
  client_operation_id: string;
  created_by: string;
  created_at: Generated<Timestamp>;
  updated_at: Generated<Timestamp>;
}

export interface CostLedgerProjectionTable {
  id: Generated<string>;
  field_id: string;
  campaign_id: string | null;
  occurred_on: ColumnType<Date, Date | string, Date | string>;
  domain_type: string;
  domain_record_id: string;
  category: string;
  amount_eur: number;
}

export interface FarmTimelineProjectionTable {
  id: Generated<string>;
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
  cost_ledger_projection: CostLedgerProjectionTable;
  farm_timeline_projection: FarmTimelineProjectionTable;
  scheduled_events: ScheduledEventTable;
}
