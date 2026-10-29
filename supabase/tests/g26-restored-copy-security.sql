\set ON_ERROR_STOP on
BEGIN;
CREATE FUNCTION pg_temp.assert_true(ok boolean, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'assertion failed: %', label; END IF; END $$;
CREATE FUNCTION pg_temp.expect_identity_denial() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  PERFORM public.list_authorized_workspaces_v1();
  RAISE EXCEPTION 'expected identity denial';
EXCEPTION WHEN insufficient_privilege THEN RETURN;
END $$;
SELECT owner_user_id AS a FROM public.workspaces WHERE deleted_at IS NULL ORDER BY id LIMIT 1 \gset
SELECT id AS c FROM auth.users WHERE id <> :'a'::uuid ORDER BY id LIMIT 1 \gset
SELECT gen_random_uuid() AS wa, gen_random_uuid() AS wb, gen_random_uuid() AS wc \gset
INSERT INTO public.workspaces(id,owner_user_id,name) VALUES
  (:'wa'::uuid, :'a'::uuid, 'G26 isolated A private'),
  (:'wb'::uuid, :'a'::uuid, 'G26 isolated A shared'),
  (:'wc'::uuid, :'c'::uuid, 'G26 isolated C private');
INSERT INTO public.workspace_memberships(workspace_id,user_id,role,permissions)
VALUES (:'wb'::uuid, :'c'::uuid, 'VIEWER', ARRAY['read']);
SELECT pg_temp.assert_true((SELECT count(*) FROM public.workspace_memberships WHERE workspace_id=:'wb'::uuid)=1,'fixture membership');
SET ROLE authenticated;
SELECT set_config('request.jwt.claims', jsonb_build_object('iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1','sub',:'a','exp',(extract(epoch from now())+900)::bigint)::text,true);
SELECT pg_temp.assert_true((public.list_authorized_workspaces_v1()->'workspaces') @> jsonb_build_array(jsonb_build_object('id',:'wa'::uuid)),'A owns private workspace');
SELECT pg_temp.assert_true((public.list_authorized_workspaces_v1()->'workspaces') @> jsonb_build_array(jsonb_build_object('id',:'wb'::uuid)),'A owns shared workspace');
SELECT pg_temp.assert_true(NOT ((public.list_authorized_workspaces_v1()->'workspaces') @> jsonb_build_array(jsonb_build_object('id',:'wc'::uuid))),'A cannot see C');
SELECT set_config('request.jwt.claims', jsonb_build_object('iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1','sub',:'c','exp',(extract(epoch from now())+900)::bigint)::text,true);
SELECT pg_temp.assert_true((public.list_authorized_workspaces_v1()->'workspaces') @> jsonb_build_array(jsonb_build_object('id',:'wb'::uuid)),'C reads shared workspace');
SELECT pg_temp.assert_true((public.list_authorized_workspaces_v1()->'workspaces') @> jsonb_build_array(jsonb_build_object('id',:'wc'::uuid)),'C owns private workspace');
SELECT pg_temp.assert_true(NOT ((public.list_authorized_workspaces_v1()->'workspaces') @> jsonb_build_array(jsonb_build_object('id',:'wa'::uuid))),'C cannot list A private');
SELECT pg_temp.assert_true((SELECT count(*) FROM public.workspaces WHERE id=:'wa'::uuid)=0,'C cannot read A private row');
WITH u AS (UPDATE public.workspaces SET name=name WHERE id=:'wa'::uuid RETURNING 1) SELECT pg_temp.assert_true(count(*)=0,'C cannot write A private row') FROM u;
SELECT pg_temp.assert_true((public.verify_workspace_access_v1(:'wb'::uuid,'read')->>'authorized')::boolean,'C read permission');
SELECT pg_temp.assert_true(NOT (public.verify_workspace_access_v1(:'wb'::uuid,'write')->>'authorized')::boolean,'C write denied');
SELECT pg_temp.assert_true(NOT has_table_privilege('authenticated','public.workspace_memberships','INSERT'),'membership direct write denied');
SELECT set_config('request.jwt.claims', jsonb_build_object('iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1','sub',:'a','exp',(extract(epoch from now())+900)::bigint)::text,true);
SELECT pg_temp.assert_true((SELECT count(*) FROM public.workspaces WHERE id=:'wa'::uuid)=1,'A regains own workspace after C');
SELECT pg_temp.assert_true((SELECT count(*) FROM public.workspaces WHERE id=:'wc'::uuid)=0,'A remains isolated from C after switch back');
SELECT set_config('request.jwt.claims', jsonb_build_object('iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1','sub',:'c','exp',(extract(epoch from now())+900)::bigint)::text,true);
RESET ROLE;
UPDATE public.workspace_memberships SET status='REVOKED',revoked_at=now(),version=version+1 WHERE workspace_id=:'wb'::uuid AND user_id=:'c'::uuid;
SET ROLE authenticated;
SELECT pg_temp.assert_true(NOT (public.verify_workspace_access_v1(:'wb'::uuid,'read')->>'authorized')::boolean,'revocation applied');
RESET ROLE;
UPDATE public.workspace_memberships SET status='ACTIVE',revoked_at=NULL,valid_from=now()-interval '2 hours',valid_until=now()-interval '1 hour',version=version+1 WHERE workspace_id=:'wb'::uuid AND user_id=:'c'::uuid;
SET ROLE authenticated;
SELECT pg_temp.assert_true(NOT (public.verify_workspace_access_v1(:'wb'::uuid,'read')->>'authorized')::boolean,'expired membership denied');
RESET ROLE;
UPDATE public.workspace_memberships SET valid_until=now()+interval '1 hour',version=version+1 WHERE workspace_id=:'wb'::uuid AND user_id=:'c'::uuid;
SET ROLE authenticated;
SELECT pg_temp.assert_true((public.verify_workspace_access_v1(:'wb'::uuid,'read')->>'authorized')::boolean,'connection recovery refresh');
SELECT set_config('request.jwt.claims', jsonb_build_object('iss','https://other.example/auth/v1','sub',:'c','exp',(extract(epoch from now())+900)::bigint)::text,true);
SELECT pg_temp.expect_identity_denial();
SELECT set_config('request.jwt.claims', jsonb_build_object('iss','https://zubpicdibqbmewxeurhm.supabase.co/auth/v1','sub',:'c','exp',(extract(epoch from now())-10)::bigint)::text,true);
SELECT pg_temp.expect_identity_denial();
SELECT set_config('request.jwt.claims','{}',true);
SELECT pg_temp.expect_identity_denial();
RESET ROLE;
SELECT pg_temp.assert_true(NOT has_function_privilege('anon','public.list_authorized_workspaces_v1()','EXECUTE'),'anon listing denied');
SELECT pg_temp.assert_true(NOT has_function_privilege('anon','public.verify_workspace_access_v1(uuid,text)','EXECUTE'),'anon verify denied');
SELECT 'G26_RESTORED_COPY_SECURITY_OK';
ROLLBACK;
