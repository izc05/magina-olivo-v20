BEGIN;

CREATE TABLE documents (
  id UUID PRIMARY KEY,
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','archived')),
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  archived_at TIMESTAMPTZ
);

CREATE TABLE document_versions (
  id UUID PRIMARY KEY,
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  version_no INTEGER NOT NULL CHECK (version_no > 0),
  storage_key TEXT NOT NULL UNIQUE,
  original_filename TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
  sha256 TEXT NOT NULL CHECK (sha256 ~ '^[0-9a-fA-F]{64}$'),
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id, version_no)
);

CREATE TABLE attachment_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  field_id UUID REFERENCES fields(id) ON DELETE CASCADE,
  domain_type TEXT,
  domain_record_id UUID,
  relation TEXT NOT NULL DEFAULT 'attachment',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (field_id IS NOT NULL OR domain_record_id IS NOT NULL)
);

CREATE TABLE ocr_runs (
  id UUID PRIMARY KEY,
  document_version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
  provider TEXT NOT NULL,
  provider_version TEXT,
  status TEXT NOT NULL CHECK (status IN ('queued','processing','succeeded','failed')),
  raw_text TEXT,
  confidence NUMERIC(6,5),
  error_code TEXT,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ
);

CREATE TABLE extraction_runs (
  id UUID PRIMARY KEY,
  ocr_run_id UUID NOT NULL REFERENCES ocr_runs(id) ON DELETE CASCADE,
  document_type TEXT NOT NULL,
  schema_version INTEGER NOT NULL DEFAULT 1,
  status TEXT NOT NULL CHECK (status IN ('queued','processing','succeeded','failed','needs_review')),
  data_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  confidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ
);

CREATE INDEX documents_workspace_idx ON documents(workspace_id, created_at DESC);
CREATE INDEX document_versions_document_idx ON document_versions(document_id, version_no DESC);
CREATE INDEX attachment_links_document_idx ON attachment_links(document_id);
CREATE INDEX attachment_links_field_idx ON attachment_links(field_id, created_at DESC);
CREATE INDEX attachment_links_domain_idx ON attachment_links(domain_type, domain_record_id);
CREATE INDEX ocr_runs_version_idx ON ocr_runs(document_version_id, created_at DESC);
CREATE INDEX extraction_runs_ocr_idx ON extraction_runs(ocr_run_id, created_at DESC);

COMMIT;
