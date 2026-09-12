BEGIN;

ALTER TABLE fields
  ADD COLUMN geometry_source TEXT,
  ADD COLUMN geometry_status TEXT NOT NULL DEFAULT 'unlocated',
  ADD COLUMN geometry_checked_at TIMESTAMPTZ,
  ADD CONSTRAINT fields_geometry_source_check CHECK (
    geometry_source IS NULL OR geometry_source IN ('manual','catastro','sigpac','import','composite')
  ),
  ADD CONSTRAINT fields_geometry_status_check CHECK (
    geometry_status IN ('unlocated','draft','verified','needs_review')
  );

CREATE UNIQUE INDEX field_land_refs_source_reference_uq
  ON field_land_refs(field_id, source, reference)
  WHERE reference IS NOT NULL;

CREATE INDEX field_land_refs_source_idx ON field_land_refs(source, reference);
CREATE INDEX field_land_refs_status_idx ON field_land_refs(field_id, status);

COMMENT ON COLUMN fields.geometry IS 'Canonical user-facing finca geometry. It is not automatically equivalent to one Catastro parcel or one SIGPAC recinto.';
COMMENT ON TABLE field_land_refs IS 'Technical territorial references linked to a finca. Catastro/SIGPAC remain references, not the finca identity.';

COMMIT;
