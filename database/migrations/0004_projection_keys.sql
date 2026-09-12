BEGIN;

ALTER TABLE farm_timeline_projection
  DROP CONSTRAINT IF EXISTS farm_timeline_projection_domain_type_domain_record_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS farm_timeline_projection_field_domain_record_uidx
  ON farm_timeline_projection(field_id, domain_type, domain_record_id);

COMMIT;
