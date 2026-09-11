BEGIN;

-- Identidad local reproducible para desarrollo con ALLOW_DEV_AUTH_HEADERS=true.
-- No usar en staging ni producción.

INSERT INTO users (id, primary_email, display_name, status)
VALUES (
  '10000000-0000-4000-8000-000000000005',
  'dev-local@example.invalid',
  'Usuario local Mágina',
  'active'
)
ON CONFLICT DO NOTHING;

INSERT INTO workspace_memberships (workspace_id, user_id, role, status)
VALUES (
  '10000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000005',
  'owner',
  'active'
)
ON CONFLICT DO NOTHING;

COMMIT;
