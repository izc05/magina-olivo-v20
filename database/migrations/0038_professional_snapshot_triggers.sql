CREATE OR REPLACE FUNCTION capture_professional_document_identity_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.issuer_snapshot_json IS NULL THEN
    SELECT jsonb_build_object(
      'workspace_id', w.id,
      'workspace_name', w.name,
      'legal_name', COALESCE(NULLIF(pbp.legal_name, ''), w.name),
      'tax_id', pbp.tax_id,
      'address', pbp.address,
      'postal_code', pbp.postal_code,
      'municipality', pbp.municipality,
      'province', pbp.province,
      'email', pbp.email,
      'phone', pbp.phone,
      'payment_terms', pbp.payment_terms,
      'footer_note', pbp.footer_note
    ) INTO NEW.issuer_snapshot_json
    FROM workspaces w
    LEFT JOIN professional_business_profiles pbp ON pbp.workspace_id = w.id
    WHERE w.id = NEW.workspace_id;
  END IF;

  IF NEW.customer_snapshot_json IS NULL THEN
    SELECT jsonb_build_object(
      'id', p.id,
      'display_name', p.display_name,
      'legal_name', p.legal_name,
      'tax_id', p.tax_id,
      'phone', p.phone,
      'email', p.email
    ) INTO NEW.customer_snapshot_json
    FROM parties p
    WHERE p.id = NEW.customer_party_id
      AND p.workspace_id = NEW.workspace_id;
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION preserve_professional_document_identity_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.issuer_snapshot_json := OLD.issuer_snapshot_json;
  NEW.customer_snapshot_json := OLD.customer_snapshot_json;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS professional_quotes_capture_identity_snapshot ON professional_quotes;
CREATE TRIGGER professional_quotes_capture_identity_snapshot
BEFORE INSERT ON professional_quotes
FOR EACH ROW EXECUTE FUNCTION capture_professional_document_identity_snapshot();

DROP TRIGGER IF EXISTS professional_invoices_capture_identity_snapshot ON professional_invoices;
CREATE TRIGGER professional_invoices_capture_identity_snapshot
BEFORE INSERT ON professional_invoices
FOR EACH ROW EXECUTE FUNCTION capture_professional_document_identity_snapshot();

DROP TRIGGER IF EXISTS professional_quotes_preserve_identity_snapshot ON professional_quotes;
CREATE TRIGGER professional_quotes_preserve_identity_snapshot
BEFORE UPDATE ON professional_quotes
FOR EACH ROW EXECUTE FUNCTION preserve_professional_document_identity_snapshot();

DROP TRIGGER IF EXISTS professional_invoices_preserve_identity_snapshot ON professional_invoices;
CREATE TRIGGER professional_invoices_preserve_identity_snapshot
BEFORE UPDATE ON professional_invoices
FOR EACH ROW EXECUTE FUNCTION preserve_professional_document_identity_snapshot();
