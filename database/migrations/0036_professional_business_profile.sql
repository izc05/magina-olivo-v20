CREATE TABLE IF NOT EXISTS professional_business_profiles (
  workspace_id uuid PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,
  legal_name text,
  tax_id text,
  address text,
  postal_code text,
  municipality text,
  province text,
  email text,
  phone text,
  payment_terms text,
  footer_note text,
  updated_by uuid REFERENCES users(id) ON DELETE SET NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE professional_business_profiles IS 'Workspace-scoped professional billing identity used to render quotes and invoices. It is presentation/configuration data, not an accounting ledger.';
