-- G2.6 security hardening for farm-parcel memberships. Candidate only; NO DEPLOY to DEV or production.
-- Load after the G0 migrations and the G1c/G2 proposals in a disposable stack.
--
-- farm_parcel_memberships carries the dated history that decides which farm a parcel belonged to.
-- Its only writers must be reviewed SECURITY DEFINER RPCs with receipts and validation
-- (today magina_g2_private.create_parcel via create_own_parcel). Two direct paths bypassed that:
--   1. the legacy "fpm_manage" FOR ALL policy: unreceipted INSERT/UPDATE/DELETE through PostgREST,
--      including hard deletes of history, for owners AND any farm grantee holding manage_farm;
--   2. hard DELETE of a parcel through "parcels_workspace_owner", cascading (ON DELETE CASCADE)
--      into every membership period of that parcel.
-- Reads keep their exact previous scope: every row "fpm_manage" exposed is also exposed by
-- "fpm_access" (owner/creator/workspace owner, or any ACTIVE grant inside its window).

DROP POLICY IF EXISTS "fpm_manage" ON public.farm_parcel_memberships;

-- Privileges as a second barrier: a future permissive policy alone cannot reopen writes.
REVOKE ALL ON public.farm_parcel_memberships FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.farm_parcel_memberships TO authenticated;

-- Archiving is a soft delete (deleted_at); a hard parcel delete only destroys history.
REVOKE DELETE, TRUNCATE ON public.parcels FROM PUBLIC, anon, authenticated;

-- Fail the transaction if any client-reachable write path to the memberships remains.
DO $$
DECLARE problem TEXT;
BEGIN
    SELECT string_agg(format('%s:%s', grantee, privilege_type), ', ') INTO problem
    FROM information_schema.role_table_grants
    WHERE table_schema = 'public' AND table_name = 'farm_parcel_memberships'
      AND grantee IN ('PUBLIC', 'anon', 'authenticated') AND privilege_type <> 'SELECT';
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'farm_parcel_memberships still writable: %', problem;
    END IF;
    SELECT string_agg(format('%s:%s', policyname, cmd), ', ') INTO problem
    FROM pg_catalog.pg_policies
    WHERE schemaname = 'public' AND tablename = 'farm_parcel_memberships' AND cmd <> 'SELECT';
    IF problem IS NOT NULL THEN
        RAISE EXCEPTION 'farm_parcel_memberships write policy remains: %', problem;
    END IF;
    IF has_table_privilege('authenticated', 'public.parcels', 'DELETE')
       OR has_table_privilege('anon', 'public.parcels', 'DELETE') THEN
        RAISE EXCEPTION 'parcels hard delete still granted';
    END IF;
END $$;
