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

export interface IrrigationRecordTable extends DomainRecordBase { occurred_at: Timestamp; duration_hours: number | null; water_m3: number | null; cost_eur: number | null; notes: string | null; }
export interface TreatmentRecordTable extends DomainRecordBase { occurred_at: Timestamp; reason: string; product_name: string; dose: string | null; quantity: string | null; applicator: string | null; equipment: string | null; cost_eur: number | null; notes: string | null; }
export interface FertilizationRecordTable extends DomainRecordBase { occurred_at: Timestamp; product_name: string; quantity_kg: number | null; application_method: string | null; composition: string | null; cost_eur: number | null; supplier: string | null; notes: string | null; }
export interface PruningRecordTable extends DomainRecordBase { occurred_at: Timestamp; pruning_type: string; workers: number | null; hours: number | null; cost_eur: number | null; notes: string | null; }
export interface ExpenseRecordTable extends DomainRecordBase { occurred_on: DateColumn; category: string; concept: string; amount_eur: number; notes: string | null; }

export interface HarvestDeliveryTable {
  id: string;
  workspace_id: string;
  campaign_id: string | null;
  cooperative_or_mill: string | null;
  delivery_at: Timestamp;
  ticket_number: string | null;
  total_kg: number;
  source: 'manual' | 'ocr' | 'import';
  client_operation_id: string;
  created_by: string;
  created_at: Generated<Timestamp>;
  updated_at: Generated<Timestamp>;
}
export interface HarvestDeliveryFieldTable { delivery_id: string; field_id: string; kg: number; }
export interface DeliveryResultTable {
  id: string;
  workspace_id: string;
  delivery_id: string;
  result_date: DateColumn;
  yield_percent: number;
  moisture_percent: number | null;
  acidity_percent: number | null;
  status: 'confirmed' | 'superseded' | 'voided';
  supersedes_id: string | null;
  client_operation_id: string;
  created_by: string;
  created_at: Generated<Timestamp>;
}

export interface DocumentTable {
  id: string;
  workspace_id: string;
  client_operation_id: string;
  kind: string;
  title: string;
  status: 'active' | 'archived';
  created_by: string;
  created_at: Generated<Timestamp>;
  archived_at: Timestamp | null;
}

export interface DocumentVersionTable {
  id: string;
  document_id: string;
  version_no: number;
  storage_key: string;
  original_filename: string;
  mime_type: string;
  byte_size: number;
  sha256: string;
  upload_status: 'reserved' | 'uploaded' | 'failed';
  integrity_status: 'pending' | 'verified' | 'unverified' | 'failed';
  uploaded_at: Timestamp | null;
  storage_etag: string | null;
  storage_checksum_sha256: string | null;
  created_by: string;
  created_at: Generated<Timestamp>;
}

export interface AttachmentLinkTable {
  id: Generated<string>;
  workspace_id: string;
  document_id: string;
  field_id: string | null;
  domain_type: string | null;
  domain_record_id: string | null;
  relation: string;
  created_at: Generated<Timestamp>;
}

export interface OcrRunTable {
  id: string;
  document_version_id: string;
  provider: string;
  provider_version: string | null;
  status: 'queued' | 'processing' | 'succeeded' | 'failed';
  raw_text: string | null;
  confidence: number | null;
  error_code: string | null;
  error_message: string | null;
  created_at: Generated<Timestamp>;
  started_at: Timestamp | null;
  completed_at: Timestamp | null;
}

export interface ExtractionRunTable {
  id: string;
  ocr_run_id: string;
  document_type: string;
  schema_version: number;
  status: 'queued' | 'processing' | 'succeeded' | 'failed' | 'needs_review';
  data_json: unknown;
  confidence_json: unknown;
  created_at: Generated<Timestamp>;
  completed_at: Timestamp | null;
}

export interface ExtractionReviewTable {
  id: string;
  workspace_id: string;
  extraction_run_id: string;
  confirmed_fields: unknown;
  corrections: unknown;
  reviewed_by: string;
  created_at: Generated<Timestamp>;
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
  harvest_deliveries: HarvestDeliveryTable;
  harvest_delivery_fields: HarvestDeliveryFieldTable;
  delivery_results: DeliveryResultTable;
  documents: DocumentTable;
  document_versions: DocumentVersionTable;
  attachment_links: AttachmentLinkTable;
  ocr_runs: OcrRunTable;
  extraction_runs: ExtractionRunTable;
  extraction_reviews: ExtractionReviewTable;
  cost_ledger_projection: CostLedgerProjectionTable;
  farm_timeline_projection: FarmTimelineProjectionTable;
  scheduled_events: ScheduledEventTable;
}
