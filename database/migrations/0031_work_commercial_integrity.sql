BEGIN;

CREATE OR REPLACE FUNCTION normalize_work_commercial_state()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.performed_for <> 'third-party' THEN
    NEW.payment_status := 'not-applicable';
    RETURN NEW;
  END IF;

  IF COALESCE(NEW.collected_eur, 0) > 0 AND NEW.charge_eur IS NULL THEN
    RAISE EXCEPTION 'professional collection requires charge_eur'
      USING ERRCODE = '23514';
  END IF;

  IF NEW.charge_eur IS NOT NULL AND COALESCE(NEW.collected_eur, 0) > NEW.charge_eur THEN
    RAISE EXCEPTION 'professional collected_eur cannot exceed charge_eur'
      USING ERRCODE = '23514';
  END IF;

  IF COALESCE(NEW.charge_eur, 0) > 0 AND COALESCE(NEW.collected_eur, 0) >= NEW.charge_eur THEN
    NEW.payment_status := 'paid';
  ELSIF COALESCE(NEW.collected_eur, 0) > 0 THEN
    NEW.payment_status := 'partial';
  ELSE
    NEW.payment_status := 'pending';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS work_records_normalize_commercial_state ON work_records;
CREATE TRIGGER work_records_normalize_commercial_state
BEFORE INSERT OR UPDATE OF performed_for, charge_eur, collected_eur, payment_status ON work_records
FOR EACH ROW
EXECUTE FUNCTION normalize_work_commercial_state();

UPDATE work_records
SET payment_status = CASE
  WHEN performed_for <> 'third-party' THEN 'not-applicable'
  WHEN COALESCE(charge_eur, 0) > 0 AND COALESCE(collected_eur, 0) >= charge_eur THEN 'paid'
  WHEN COALESCE(collected_eur, 0) > 0 THEN 'partial'
  ELSE 'pending'
END;

ALTER TABLE work_records
  DROP CONSTRAINT IF EXISTS work_collection_not_above_charge,
  ADD CONSTRAINT work_collection_not_above_charge CHECK (
    collected_eur IS NULL
    OR collected_eur = 0
    OR (charge_eur IS NOT NULL AND collected_eur <= charge_eur)
  );

COMMIT;
