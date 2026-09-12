BEGIN;

ALTER TABLE work_records
  ADD COLUMN collected_eur NUMERIC(12,2),
  ADD COLUMN payment_status TEXT NOT NULL DEFAULT 'not-applicable'
    CHECK (payment_status IN ('not-applicable','pending','partial','paid')),
  ADD COLUMN invoice_reference TEXT;

ALTER TABLE work_records
  ADD CONSTRAINT work_commercial_amounts_nonnegative CHECK (
    (quoted_amount_eur IS NULL OR quoted_amount_eur >= 0) AND
    (charge_eur IS NULL OR charge_eur >= 0) AND
    (collected_eur IS NULL OR collected_eur >= 0)
  ),
  ADD CONSTRAINT work_self_payment_state CHECK (
    performed_for = 'third-party' OR payment_status = 'not-applicable'
  );

CREATE INDEX work_records_payment_status_idx
  ON work_records(workspace_id, payment_status)
  WHERE performed_for = 'third-party';

COMMIT;
