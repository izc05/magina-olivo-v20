\set ON_ERROR_STOP 1
BEGIN;
DO $$ BEGIN
  IF NOT has_function_privilege('authenticated','public.put_own_farm_cycles_v1(uuid,uuid,bigint,jsonb)','EXECUTE')
     OR has_function_privilege('anon','public.put_own_farm_cycles_v1(uuid,uuid,bigint,jsonb)','EXECUTE')
     OR has_function_privilege('anon','public.get_own_farm_cycles_v1(uuid)','EXECUTE')
     OR has_table_privilege('authenticated','public.agricultural_cycles','SELECT')
     OR has_table_privilege('anon','public.agricultural_cycle_farm_heads','INSERT')
     OR NOT (SELECT relrowsecurity FROM pg_class WHERE oid='public.agricultural_cycles'::regclass)
     OR has_function_privilege('authenticated','magina_g2_private.cycle_actor()','EXECUTE') THEN
    RAISE EXCEPTION 'P4 grants divergence';
  END IF;
END $$;
INSERT INTO auth.users(id,aud,role,email) VALUES ('0000000a-0000-4000-8000-0000000000a1','authenticated','authenticated','p4-a@example.test'),
  ('0000000c-0000-4000-8000-0000000000c1','authenticated','authenticated','p4-c@example.test');
INSERT INTO public.workspaces(id,owner_user_id,name) VALUES ('0000000a-0000-4000-8000-00000000a001','0000000a-0000-4000-8000-0000000000a1','P4 rehearsal');
INSERT INTO public.farms(id,workspace_id,creator_user_id,name) VALUES ('0000000a-0000-4000-8000-00000000f001','0000000a-0000-4000-8000-00000000a001','0000000a-0000-4000-8000-0000000000a1','P4 farm');
INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,status) VALUES ('0000000a-0000-4000-8000-00000000c001','0000000a-0000-4000-8000-00000000a001','0000000a-0000-4000-8000-00000000f001','Recogida','2026-11-07','CLOSED');
CREATE TEMP TABLE results(step text, value text);
GRANT ALL ON results TO authenticated, anon;
SELECT set_config('request.jwt.claims', json_build_object('iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1','sub','0000000a-0000-4000-8000-0000000000a1',
  'role','authenticated','is_anonymous',false,'exp',extract(epoch from now())::bigint+600)::text, true);
SET LOCAL ROLE authenticated;
INSERT INTO results SELECT 'put1', public.put_own_farm_cycles_v1('0000000a-0000-4000-8000-0000000e0001','0000000a-0000-4000-8000-00000000f001',0,
  '{"workspaceId":"0000000a-0000-4000-8000-00000000a001","cycles":[{"id":"0000000a-0000-4000-8000-00000000b001","label":"2026/27","startDate":"2026-11-07","effectiveEndDate":null,"closedAt":null,"noRecollection":false}],"recollections":[{"campaignId":"0000000a-0000-4000-8000-00000000c001","cycleId":"0000000a-0000-4000-8000-00000000b001"}]}')->>'remote_version';
INSERT INTO results SELECT 'replay', public.put_own_farm_cycles_v1('0000000a-0000-4000-8000-0000000e0001','0000000a-0000-4000-8000-00000000f001',0,
  '{"workspaceId":"0000000a-0000-4000-8000-00000000a001","cycles":[{"id":"0000000a-0000-4000-8000-00000000b001","label":"2026/27","startDate":"2026-11-07","effectiveEndDate":null,"closedAt":null,"noRecollection":false}],"recollections":[{"campaignId":"0000000a-0000-4000-8000-00000000c001","cycleId":"0000000a-0000-4000-8000-00000000b001"}]}')->>'idempotent';
INSERT INTO results SELECT 'close', public.put_own_farm_cycles_v1('0000000a-0000-4000-8000-0000000e0002','0000000a-0000-4000-8000-00000000f001',1,
  '{"workspaceId":"0000000a-0000-4000-8000-00000000a001","cycles":[{"id":"0000000a-0000-4000-8000-00000000b001","label":"2026/27","startDate":"2026-11-07","effectiveEndDate":"2027-01-31","closedAt":"2027-02-01T10:00:00.123456Z","noRecollection":false}],"recollections":[{"campaignId":"0000000a-0000-4000-8000-00000000c001","cycleId":"0000000a-0000-4000-8000-00000000b001"}]}')->>'remote_version';
INSERT INTO results SELECT 'get', public.get_own_farm_cycles_v1('0000000a-0000-4000-8000-00000000f001')::text;
DO $$ BEGIN
  PERFORM public.put_own_farm_cycles_v1('0000000a-0000-4000-8000-0000000e0003','0000000a-0000-4000-8000-00000000f001',1,
    '{"workspaceId":"0000000a-0000-4000-8000-00000000a001","cycles":[],"recollections":[]}');
  RAISE EXCEPTION 'stale accepted';
EXCEPTION WHEN unique_violation THEN INSERT INTO results VALUES ('stale', SQLERRM);
END $$;
RESET ROLE;
SELECT set_config('request.jwt.claims', json_build_object('iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1','sub','0000000c-0000-4000-8000-0000000000c1',
  'role','authenticated','is_anonymous',false,'exp',extract(epoch from now())::bigint+600)::text, true);
SET LOCAL ROLE authenticated;
DO $$ BEGIN
  PERFORM public.get_own_farm_cycles_v1('0000000a-0000-4000-8000-00000000f001');
  RAISE EXCEPTION 'foreign read accepted';
EXCEPTION WHEN insufficient_privilege THEN INSERT INTO results VALUES ('foreign', 'denied');
END $$;
RESET ROLE;
SET LOCAL ROLE anon;
DO $$ BEGIN
  PERFORM public.get_own_farm_cycles_v1('0000000a-0000-4000-8000-00000000f001');
  RAISE EXCEPTION 'anon accepted';
EXCEPTION WHEN insufficient_privilege THEN INSERT INTO results VALUES ('anon', 'denied');
END $$;
RESET ROLE;
SELECT step||' = '||value FROM results;
ROLLBACK;
