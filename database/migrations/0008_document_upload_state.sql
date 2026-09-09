BEGIN;

ALTER TABLE document_versions
  ADD COLUMN upload_status TEXT NOT NULL DEFAULT 'reserved'
    CHECK (upload_status IN ('reserved','uploaded','failed')),
  ADD COLUMN integrity_status TEXT NOT NULL DEFAULT 'pending'
    CHECK (integrity_status IN ('pending','verified','unverified','failed')),
  ADD COLUMN uploaded_at TIMESTAMPTZ,
  ADD COLUMN storage_etag TEXT,
  ADD COLUMN storage_checksum_sha256 TEXT;

CREATE INDEX document_versions_upload_status_idx
  ON document_versions(upload_status, created_at);

COMMIT;
