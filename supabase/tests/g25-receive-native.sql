-- Disposable PostgreSQL 17 harness only. Claims model PostgREST; this does not
-- validate JWT signatures. All agricultural rows below are synthetic and local.
BEGIN;
INSERT INTO auth.users(id) VALUES
  ('008ea900-5f3c-407f-8917-ad72bc9d955b'),
  ('49d1dbbb-d974-4eca-8bd7-1e51c83253f5');
-- BEGIN G25 SEED (reused with real ephemeral Auth IDs by the native gateway test)
INSERT INTO public.workspaces(id,owner_user_id,name) VALUES
  ('11111111-1111-4111-8111-111111111111','008ea900-5f3c-407f-8917-ad72bc9d955b','G25 local fixture');
INSERT INTO public.farms(id,owner_id,creator_user_id,workspace_id,name) VALUES
  ('22222222-2222-4222-8222-222222222222','008ea900-5f3c-407f-8917-ad72bc9d955b',
   '008ea900-5f3c-407f-8917-ad72bc9d955b','11111111-1111-4111-8111-111111111111','G25 farm');
INSERT INTO public.parcels(id,workspace_id,display_name) VALUES
  ('33333333-3333-4333-8333-333333333333','11111111-1111-4111-8111-111111111111','G25 parcel'),
  ('33333333-3333-4333-8333-333333333334','11111111-1111-4111-8111-111111111111','G25 wrong parcel');
INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id) VALUES
  ('44444444-4444-4444-8444-444444444444','11111111-1111-4111-8111-111111111111',
   '22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333');
INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,snapshot_format) VALUES
  ('55555555-5555-4555-8555-555555555555','11111111-1111-4111-8111-111111111111',
   '22222222-2222-4222-8222-222222222222','G25 season','2026-01-01',1);
INSERT INTO public.campaign_parcels(id,workspace_id,campaign_id,parcel_id,farm_id_at_start,
  farm_name_at_start,parcel_name_at_start) VALUES
  ('66666666-6666-4666-8666-666666666666','11111111-1111-4111-8111-111111111111',
   '55555555-5555-4555-8555-555555555555','33333333-3333-4333-8333-333333333333',
   '22222222-2222-4222-8222-222222222222','G25 farm','G25 parcel');
INSERT INTO public.harvests(id,workspace_id,farm_id,campaign_id,harvest_date,weight_grams,
  recorded_by_user_id) VALUES
  ('77777777-7777-4777-8777-777777777777','11111111-1111-4111-8111-111111111111',
   '22222222-2222-4222-8222-222222222222','55555555-5555-4555-8555-555555555555',
   '2026-10-25',1540000,'008ea900-5f3c-407f-8917-ad72bc9d955b');
INSERT INTO public.harvest_parcels(id,workspace_id,harvest_id,parcel_id,campaign_parcel_id,
  parcel_name_at_harvest,allocation_mode) VALUES
  ('88888888-8888-4888-8888-888888888888','11111111-1111-4111-8111-111111111111',
   '77777777-7777-4777-8777-777777777777','33333333-3333-4333-8333-333333333333',
   '66666666-6666-4666-8666-666666666666','G25 parcel','UNALLOCATED');
INSERT INTO public.deliveries(id,workspace_id,farm_id,campaign_id,harvest_id,delivery_date,
  destination_name,net_grams,recorded_by_user_id,snapshot_format) VALUES
  ('99999999-9999-4999-8999-999999999999','11111111-1111-4111-8111-111111111111',
   '22222222-2222-4222-8222-222222222222','55555555-5555-4555-8555-555555555555',
   '77777777-7777-4777-8777-777777777777','2026-10-25','G25 mill',1540000,
   '008ea900-5f3c-407f-8917-ad72bc9d955b',2);
INSERT INTO public.delivery_parcels(id,workspace_id,delivery_id,parcel_id,campaign_parcel_id,
  parcel_name_at_delivery,allocation_mode) VALUES
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111',
   '99999999-9999-4999-8999-999999999999','33333333-3333-4333-8333-333333333333',
   '66666666-6666-4666-8666-666666666666','G25 parcel','UNALLOCATED');
INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,
  payload_hash,result_version,payload_version) VALUES
  ('008ea900-5f3c-407f-8917-ad72bc9d955b','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
   'DELIVERY','99999999-9999-4999-8999-999999999999',repeat('a',64),1,2);
-- END G25 SEED

SELECT set_config('request.jwt.claim.sub','008ea900-5f3c-407f-8917-ad72bc9d955b',false);
SELECT set_config('request.jwt.claims',jsonb_build_object('sub','008ea900-5f3c-407f-8917-ad72bc9d955b',
 'role','authenticated','iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1',
 'exp',extract(epoch FROM now())::bigint+3600,'is_anonymous',false)::text,false);
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'Expected missing origin to fail';
EXCEPTION WHEN SQLSTATE '22023' THEN
  IF SQLERRM NOT LIKE '%unverified origin%' THEN RAISE; END IF;
END $$;
RESET ROLE;

-- These origin values are synthetic fixture data, never a backfill of DEV.
UPDATE public.campaign_parcels SET origin_created_at='2026-01-01 00:00+00',
  origin_updated_at='2026-01-01 00:00+00',origin_version=1,origin_evidence_sha256=repeat('b',64);
UPDATE public.harvest_parcels SET origin_created_at='2026-10-25 00:00+00',
  origin_updated_at='2026-10-25 00:00+00',origin_version=1,origin_evidence_sha256=repeat('b',64);
UPDATE public.delivery_parcels SET origin_created_at='2026-10-25 00:00+00',
  origin_updated_at='2026-10-25 00:00+00',origin_version=1,origin_evidence_sha256=repeat('b',64);
SET ROLE authenticated;
DO $$ DECLARE b jsonb; BEGIN
  b := public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  IF b->>'account_id' <> '008ea900-5f3c-407f-8917-ad72bc9d955b'
    OR b->>'head_payload_hash' <> repeat('a',64)
    OR (b->'delivery'->>'net_grams')::bigint <> 1540000
    OR b->'harvest_parcel'->>'id' <> '88888888-8888-4888-8888-888888888888'
    OR b->'campaign_parcel'->>'origin_version' <> '1' THEN
    RAISE EXCEPTION 'Authorized bundle mismatch';
  END IF;
END $$;
RESET ROLE;

SAVEPOINT missing_harvest_share;
DELETE FROM public.harvest_parcels WHERE id='88888888-8888-4888-8888-888888888888';
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'Missing harvest share should fail';
EXCEPTION WHEN SQLSTATE '22023' THEN
  IF SQLERRM NOT LIKE '%harvest allocation%' THEN RAISE; END IF;
END $$;
RESET ROLE;
ROLLBACK TO SAVEPOINT missing_harvest_share;

SAVEPOINT missing_delivery_origin;
UPDATE public.delivery_parcels SET origin_created_at=NULL,origin_updated_at=NULL,
  origin_version=NULL,origin_evidence_sha256=NULL;
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'Missing delivery origin should fail';
EXCEPTION WHEN SQLSTATE '22023' THEN
  IF SQLERRM NOT LIKE '%unverified origin%' THEN RAISE; END IF;
END $$;
RESET ROLE;
ROLLBACK TO SAVEPOINT missing_delivery_origin;

SAVEPOINT unsupported_relation;
UPDATE public.deliveries SET destination_organization_id='cccccccc-cccc-4ccc-8ccc-cccccccccccc'
  WHERE id='99999999-9999-4999-8999-999999999999';
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'Unsupported organization should fail';
EXCEPTION WHEN SQLSTATE '22023' THEN
  IF SQLERRM NOT LIKE '%unsupported linked record%' THEN RAISE; END IF;
END $$;
RESET ROLE;
ROLLBACK TO SAVEPOINT unsupported_relation;

SELECT set_config('request.jwt.claim.sub','49d1dbbb-d974-4eca-8bd7-1e51c83253f5',false);
SELECT set_config('request.jwt.claims',jsonb_build_object('sub','49d1dbbb-d974-4eca-8bd7-1e51c83253f5',
 'role','authenticated','iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1',
 'exp',extract(epoch FROM now())::bigint+3600,'is_anonymous',false)::text,false);
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'C should be denied';
EXCEPTION WHEN SQLSTATE '42501' THEN NULL;
END $$;
RESET ROLE;

SELECT set_config('request.jwt.claim.sub','008ea900-5f3c-407f-8917-ad72bc9d955b',false);
SELECT set_config('request.jwt.claims',jsonb_build_object('sub','008ea900-5f3c-407f-8917-ad72bc9d955b',
 'role','authenticated','iss','https://other-project.supabase.co/auth/v1',
 'exp',extract(epoch FROM now())::bigint+3600,'is_anonymous',false)::text,false);
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'Foreign issuer should be denied';
EXCEPTION WHEN SQLSTATE '42501' THEN NULL;
END $$;
RESET ROLE;

SELECT set_config('request.jwt.claims',jsonb_build_object('sub','008ea900-5f3c-407f-8917-ad72bc9d955b',
 'role','authenticated','iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1',
 'exp',extract(epoch FROM now())::bigint+3600,'is_anonymous',false)::text,false);
SAVEPOINT cross_relation;
UPDATE public.harvest_parcels SET parcel_id='33333333-3333-4333-8333-333333333334';
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'Crossed parcel should fail';
EXCEPTION WHEN SQLSTATE '22023' THEN NULL;
END $$;
RESET ROLE;
ROLLBACK TO SAVEPOINT cross_relation;

SAVEPOINT changed_head;
UPDATE public.deliveries SET notes='newer HEAD' WHERE id='99999999-9999-4999-8999-999999999999';
SET ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_delivery_receive_v1('99999999-9999-4999-8999-999999999999');
  RAISE EXCEPTION 'Changed HEAD should fail';
EXCEPTION WHEN SQLSTATE '22023' THEN
  IF SQLERRM NOT LIKE '%head changed%' THEN RAISE; END IF;
END $$;
RESET ROLE;
ROLLBACK TO SAVEPOINT changed_head;

DO $$ BEGIN
  IF has_table_privilege('anon','public.harvest_parcels','SELECT')
    OR has_table_privilege('authenticated','public.harvest_parcels','SELECT')
    OR has_table_privilege('authenticated','public.harvest_parcels','INSERT')
    OR has_function_privilege('anon','public.get_own_delivery_receive_v1(uuid)','EXECUTE') THEN
    RAISE EXCEPTION 'Grant violation';
  END IF;
END $$;
ROLLBACK;
