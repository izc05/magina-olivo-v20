-- G2.6 #808 R2: receive contract for Jornadas (HARVEST) with all their Pesadas (DELIVERY).
-- Proposal only: disposable Supabase. NEVER apply to DEV or production without a separate decision.
-- Load after g26-historical-writers-v1.sql (historical_actor, fact_origins), g26-harvest-writer-v1.sql,
-- g26-delivery-delete-v1.sql (R1-a) and g26-harvest-head-v2.sql (R1-c, embedded in the bundle).
-- Design: docs/07-plans/G2.6-808-RECEIVER-DESIGN-2026-11-03.md (D1, D2, D4, D8, §3); owner GO on #832.
--
-- Additive: two new read functions. No table, column, constraint, RLS policy, grant, trigger or existing
-- function changes. Both are STABLE: every statement inside sees ONE snapshot of the calling query (D2).
--
-- Identity (both): historical_actor() -- authenticated, literal issuer, unexpired, not anonymous, sub = actor.
-- Scope (both): owner-only v1 (decision 3 on #827). The actor owns the workspace NOW (not soft-deleted); the farm
-- and the campaign belong to it (campaign not soft-deleted); the farm capability 'view_deliveries' holds. Anything
-- unknown, foreign or not owned answers exactly 'sync denied: SCOPE' (no id, count or existence leaks). Only once
-- the actor is proven owner, a farm that is not live and ACTIVE is 'sync denied: FARM_NOT_LIVE'.
-- Limits: never truncated. Over a limit: SQLSTATE 54000 'sync limit: <CAUSE>' with DETAIL '<what>=<n>'.
--
-- get_own_harvest_manifest_v1(p_workspace_id, p_farm_id, p_campaign_id, p_from DATE, p_to DATE):
--   ids, versions and tombstones of every Jornada and Pesada of that farm and campaign, live AND deleted.
--   * Partition (p_from/p_to inclusive, each optional): a Jornada by its harvest_date; a Pesada by the date of
--     its Jornada, or by its own delivery_date when it has none (partition_date). Disjoint ranges therefore give
--     disjoint manifests whose union is exactly the unpartitioned one.
--   * Limits: 2000 Jornadas (MANIFEST_HARVESTS), 20000 Pesadas (MANIFEST_DELIVERIES), 4 MiB (MANIFEST_BYTES).
--   * recorded_by_self: false for a Pesada or Jornada written by another account (the receiver retains it:
--     FOREIGN_RECORDER, D8). No permission is granted to anyone by this read.
--
-- get_own_harvest_bundle_v1(p_harvest_id): one Jornada with ALL its Pesadas (live and deleted), from one snapshot.
--   * harvest: the full row; harvest_head: get_own_harvest_head_v2 (R1-c: strict HEAD, own and derived fingerprints).
--   * server_weight_grams and server_harvest_parcels: for comparison only (D5), never to be imported.
--   * per Pesada: the full row, its parcels (by parcel_id), origin_created_at_exact from fact_origins (or null),
--     recorded_by_self, and head = the ONE applied DELIVERY receipt of THIS actor at the current version
--     (operation_id, version, payload_version, payload_hash, kind WRITE|DELETE), or null. Same strictness as
--     get_own_delivery_head_v3 / get_own_delivery_delete_head_v1: zero or several candidates give null, never a guess.
--   * Limits: 500 Pesadas (BUNDLE_DELIVERIES), 4 MiB (BUNDLE_BYTES).

CREATE FUNCTION public.get_own_harvest_manifest_v1(p_workspace_id UUID, p_farm_id UUID, p_campaign_id UUID,
    p_from DATE DEFAULT NULL, p_to DATE DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID; farm public.farms%ROWTYPE; harvest_count BIGINT; delivery_count BIGINT; result JSONB;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_workspace_id IS NULL OR p_farm_id IS NULL OR p_campaign_id IS NULL OR p_from > p_to THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.workspaces w WHERE w.id=p_workspace_id AND w.owner_user_id=actor AND w.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT * INTO farm FROM public.farms f WHERE f.id=p_farm_id AND f.workspace_id=p_workspace_id;
    IF NOT FOUND OR NOT EXISTS (SELECT 1 FROM public.campaigns c WHERE c.id=p_campaign_id AND c.farm_id=p_farm_id
        AND c.workspace_id=p_workspace_id AND c.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    IF farm.deleted_at IS NOT NULL OR farm.status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'sync denied: FARM_NOT_LIVE' USING ERRCODE='42501';
    END IF;
    IF NOT public.has_farm_capability(p_farm_id, 'view_deliveries') THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT count(*) INTO harvest_count FROM public.harvests h
        WHERE h.workspace_id=p_workspace_id AND h.farm_id=p_farm_id AND h.campaign_id=p_campaign_id
          AND (p_from IS NULL OR h.harvest_date>=p_from) AND (p_to IS NULL OR h.harvest_date<=p_to);
    IF harvest_count>2000 THEN
        RAISE EXCEPTION 'sync limit: MANIFEST_HARVESTS' USING ERRCODE='54000', DETAIL='harvests='||harvest_count;
    END IF;
    SELECT count(*) INTO delivery_count FROM public.deliveries d LEFT JOIN public.harvests h ON h.id=d.harvest_id
        WHERE d.workspace_id=p_workspace_id AND d.farm_id=p_farm_id AND d.campaign_id=p_campaign_id
          AND (p_from IS NULL OR coalesce(h.harvest_date,d.delivery_date)>=p_from)
          AND (p_to IS NULL OR coalesce(h.harvest_date,d.delivery_date)<=p_to);
    IF delivery_count>20000 THEN
        RAISE EXCEPTION 'sync limit: MANIFEST_DELIVERIES' USING ERRCODE='54000', DETAIL='deliveries='||delivery_count;
    END IF;
    result := jsonb_build_object('manifest_contract_version',1,'issuer',auth.jwt()->>'iss','account_id',actor,
        'workspace_id',p_workspace_id,'farm_id',p_farm_id,'campaign_id',p_campaign_id,
        'from',to_char(p_from,'YYYY-MM-DD'),'to',to_char(p_to,'YYYY-MM-DD'),
        'harvests',coalesce((SELECT jsonb_agg(jsonb_build_object('id',h.id,'version',h.version,'deleted',h.deleted_at IS NOT NULL,
                'day_origin',h.day_origin,'harvest_date',to_char(h.harvest_date,'YYYY-MM-DD'),
                'recorded_by_self',h.recorded_by_user_id=actor) ORDER BY h.id::text)
            FROM public.harvests h
            WHERE h.workspace_id=p_workspace_id AND h.farm_id=p_farm_id AND h.campaign_id=p_campaign_id
              AND (p_from IS NULL OR h.harvest_date>=p_from) AND (p_to IS NULL OR h.harvest_date<=p_to)),'[]'::jsonb),
        'deliveries',coalesce((SELECT jsonb_agg(jsonb_build_object('id',d.id,'harvest_id',d.harvest_id,'version',d.version,
                'deleted',d.deleted_at IS NOT NULL,'partition_date',to_char(coalesce(h.harvest_date,d.delivery_date),'YYYY-MM-DD'),
                'recorded_by_self',d.recorded_by_user_id=actor) ORDER BY d.id::text)
            FROM public.deliveries d LEFT JOIN public.harvests h ON h.id=d.harvest_id
            WHERE d.workspace_id=p_workspace_id AND d.farm_id=p_farm_id AND d.campaign_id=p_campaign_id
              AND (p_from IS NULL OR coalesce(h.harvest_date,d.delivery_date)>=p_from)
              AND (p_to IS NULL OR coalesce(h.harvest_date,d.delivery_date)<=p_to)),'[]'::jsonb));
    IF octet_length(result::text)>4194304 THEN
        RAISE EXCEPTION 'sync limit: MANIFEST_BYTES' USING ERRCODE='54000', DETAIL='bytes='||octet_length(result::text);
    END IF;
    RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.get_own_harvest_manifest_v1(UUID,UUID,UUID,DATE,DATE) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_harvest_manifest_v1(UUID,UUID,UUID,DATE,DATE) TO authenticated;

CREATE FUNCTION public.get_own_harvest_bundle_v1(p_harvest_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID; h public.harvests%ROWTYPE; farm public.farms%ROWTYPE; delivery_count BIGINT; result JSONB;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_harvest_id IS NULL THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    SELECT * INTO h FROM public.harvests WHERE id=p_harvest_id;
    IF NOT FOUND OR NOT EXISTS (SELECT 1 FROM public.workspaces w WHERE w.id=h.workspace_id AND w.owner_user_id=actor AND w.deleted_at IS NULL)
       OR (h.campaign_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM public.campaigns c WHERE c.id=h.campaign_id
           AND c.farm_id=h.farm_id AND c.workspace_id=h.workspace_id AND c.deleted_at IS NULL)) THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT * INTO farm FROM public.farms f WHERE f.id=h.farm_id AND f.workspace_id=h.workspace_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    IF farm.deleted_at IS NOT NULL OR farm.status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'sync denied: FARM_NOT_LIVE' USING ERRCODE='42501';
    END IF;
    IF NOT public.has_farm_capability(h.farm_id, 'view_deliveries') THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT count(*) INTO delivery_count FROM public.deliveries d WHERE d.harvest_id=h.id AND d.workspace_id=h.workspace_id;
    IF delivery_count>500 THEN
        RAISE EXCEPTION 'sync limit: BUNDLE_DELIVERIES' USING ERRCODE='54000', DETAIL='deliveries='||delivery_count;
    END IF;
    result := jsonb_build_object('bundle_contract_version',1,'issuer',auth.jwt()->>'iss','account_id',actor,
        'harvest',to_jsonb(h),
        'harvest_head',public.get_own_harvest_head_v2(h.id),
        'server_weight_grams',h.weight_grams,
        'server_harvest_parcels',coalesce((SELECT jsonb_agg(jsonb_build_object('parcel_id',hp.parcel_id,'weight_grams',hp.weight_grams,
                'allocation_mode',hp.allocation_mode) ORDER BY hp.parcel_id::text)
            FROM public.harvest_parcels hp WHERE hp.harvest_id=h.id),'[]'::jsonb),
        'deliveries',coalesce((SELECT jsonb_agg(jsonb_build_object(
                'delivery',to_jsonb(d),
                'parcels',coalesce((SELECT jsonb_agg(to_jsonb(p) ORDER BY p.parcel_id::text, p.id::text)
                    FROM public.delivery_parcels p WHERE p.delivery_id=d.id),'[]'::jsonb),
                'origin_created_at_exact',(SELECT o.origin_created_at_exact FROM magina_g2_private.fact_origins o
                    WHERE o.entity_type='DELIVERY' AND o.entity_id=d.id),
                'recorded_by_self',d.recorded_by_user_id=actor,
                'head',(SELECT CASE WHEN count(*)=1 THEN jsonb_build_object(
                        'operation_id',(array_agg(r.operation_id))[1],'version',(array_agg(r.result_version))[1],
                        'payload_version',(array_agg(r.payload_version))[1],'payload_hash',(array_agg(r.payload_hash))[1],
                        'kind',CASE WHEN d.deleted_at IS NULL THEN 'WRITE' ELSE 'DELETE' END) END
                    FROM magina_g2_private.sync_operations r
                    WHERE r.account_id=actor AND r.entity_type='DELIVERY' AND r.entity_id=d.id
                      AND r.result_version=d.version AND r.status='APPLIED'))
                ORDER BY d.id::text)
            FROM public.deliveries d WHERE d.harvest_id=h.id AND d.workspace_id=h.workspace_id),'[]'::jsonb));
    IF octet_length(result::text)>4194304 THEN
        RAISE EXCEPTION 'sync limit: BUNDLE_BYTES' USING ERRCODE='54000', DETAIL='bytes='||octet_length(result::text);
    END IF;
    RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.get_own_harvest_bundle_v1(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_harvest_bundle_v1(UUID) TO authenticated;

-- Self-check: two authenticated-only reads.
DO $$
DECLARE fn TEXT;
BEGIN
    FOREACH fn IN ARRAY ARRAY['public.get_own_harvest_manifest_v1(uuid,uuid,uuid,date,date)','public.get_own_harvest_bundle_v1(uuid)'] LOOP
        IF has_function_privilege('anon', fn, 'EXECUTE') OR has_function_privilege('public', fn, 'EXECUTE')
           OR NOT has_function_privilege('authenticated', fn, 'EXECUTE') THEN
            RAISE EXCEPTION '% privileges are not exactly authenticated-only', fn;
        END IF;
    END LOOP;
END $$;
