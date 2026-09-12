BEGIN;

INSERT INTO workspaces (id, name, type)
VALUES ('10000000-0000-4000-8000-000000000001', 'Explotación demo Mágina', 'family')
ON CONFLICT (id) DO NOTHING;

INSERT INTO campaigns (id, workspace_id, name, start_date, end_date, status)
VALUES (
  '10000000-0000-4000-8000-000000000002',
  '10000000-0000-4000-8000-000000000001',
  '2026/27',
  '2026-09-01',
  '2027-08-31',
  'active'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fields (
  id,
  workspace_id,
  client_operation_id,
  name,
  municipality,
  province,
  tree_count,
  crop,
  variety,
  water_regime,
  status
)
VALUES (
  '10000000-0000-4000-8000-000000000003',
  '10000000-0000-4000-8000-000000000001',
  '10000000-0000-4000-8000-000000000004',
  'Las Cenillas',
  'Huelma',
  'Jaén',
  23,
  'olivar',
  'Picual',
  'secano',
  'active'
)
ON CONFLICT (id) DO NOTHING;

COMMIT;
