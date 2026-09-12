BEGIN;

CREATE TABLE platform_admins (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('super_admin','admin','editor','support')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','revoked')),
  granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX platform_admins_status_role_idx
  ON platform_admins(status, role, updated_at DESC);

CREATE TABLE cms_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type TEXT NOT NULL CHECK (type IN ('page','news','event','place','mill','directory','promotion','alert')),
  slug TEXT NOT NULL CHECK (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
  title TEXT NOT NULL,
  summary TEXT,
  content_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','published','archived')),
  featured BOOLEAN NOT NULL DEFAULT false,
  starts_at TIMESTAMPTZ,
  ends_at TIMESTAMPTZ,
  media_url TEXT,
  external_url TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_by UUID NOT NULL REFERENCES users(id),
  updated_by UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ,
  UNIQUE (type, slug),
  CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at)
);

CREATE INDEX cms_entries_public_idx
  ON cms_entries(type, status, featured, sort_order, published_at DESC);
CREATE INDEX cms_entries_updated_idx
  ON cms_entries(updated_at DESC);

CREATE TABLE site_settings (
  key TEXT PRIMARY KEY CHECK (key ~ '^[a-z0-9]+(?:[._-][a-z0-9]+)*$'),
  value_json JSONB NOT NULL,
  description TEXT,
  is_public BOOLEAN NOT NULL DEFAULT false,
  updated_by UUID NOT NULL REFERENCES users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX site_settings_public_idx ON site_settings(is_public, key);

CREATE TABLE admin_audit_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  actor_role TEXT NOT NULL CHECK (actor_role IN ('super_admin','admin','editor','support','bootstrap')),
  action TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_id TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX admin_audit_log_created_idx
  ON admin_audit_log(created_at DESC);
CREATE INDEX admin_audit_log_actor_idx
  ON admin_audit_log(actor_user_id, created_at DESC);

COMMIT;
