ALTER TABLE professional_quotes
  ADD COLUMN IF NOT EXISTS issuer_snapshot_json jsonb,
  ADD COLUMN IF NOT EXISTS customer_snapshot_json jsonb;

ALTER TABLE professional_invoices
  ADD COLUMN IF NOT EXISTS issuer_snapshot_json jsonb,
  ADD COLUMN IF NOT EXISTS customer_snapshot_json jsonb;

COMMENT ON COLUMN professional_quotes.issuer_snapshot_json IS 'Immutable-ish presentation snapshot captured when the quote is created so later profile edits do not rewrite historical commercial documents.';
COMMENT ON COLUMN professional_quotes.customer_snapshot_json IS 'Customer identity snapshot captured when the quote is created.';
COMMENT ON COLUMN professional_invoices.issuer_snapshot_json IS 'Presentation snapshot captured when the invoice is created so later billing-profile edits do not rewrite historical invoices.';
COMMENT ON COLUMN professional_invoices.customer_snapshot_json IS 'Customer identity snapshot captured when the invoice is created.';
