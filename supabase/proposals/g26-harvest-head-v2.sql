-- G2.6 #808 R1-c: Jornada (HARVEST) HEAD v2 with deterministic canonical fingerprints.
-- Proposal only: disposable Supabase. NEVER apply to DEV or production without a separate decision.
-- Load after g26-historical-writers-v1.sql (historical_actor) and g26-harvest-writer-v1.sql (harvests.day_origin,
-- derive_auto_day, HARVEST receipts, get_own_harvest_head_v1).
-- Design: docs/07-plans/G2.6-808-RECEIVER-DESIGN-2026-11-03.md (H4, R1-c) and owner decision 2 on #827.
--
-- Additive. get_own_harvest_head_v1 and every table, column, constraint, RLS policy, grant, trigger and existing
-- function keep their exact definitions. One new function: public.get_own_harvest_head_v2.
--
-- Contract of get_own_harvest_head_v2(p_harvest_id):
--   * Identity and scope exactly as v1: historical_actor() (authenticated, literal issuer, unexpired, not anonymous);
--     the actor owns the Jornada's workspace NOW (not soft-deleted), else 'sync denied: SCOPE' (unknown, foreign
--     and not-owned look the same); its farm is live and ACTIVE, else 'sync denied: FARM_NOT_LIVE'.
--   * HEAD exactly as v1: head_* name the ONE applied HARVEST receipt of THIS actor at the current version, or are
--     all null. version, deleted, day_origin and weight_grams are the row's, as in v1.
--   * own_fingerprint: sha256 (lowercase hex) of the canonical jsonb text of the 11 fields a client writes, with the
--     SAME keys and value shapes as p_harvest of sync_own_harvest_v1 minus baseVersion:
--       id, farmId, campaignId, harvestDate (YYYY-MM-DD), dayOrigin ('AUTO_DAY', anything else 'MANUAL'),
--       weightGrams (MANUAL: the stored kilos; AUTO_DAY: null, because they are derived), destination, notes,
--       collectionMethod, workerCount, machineryText (absent values are JSON null).
--     It is independent of the version, the tombstone, the receipt envelope (issuer, localVersion) and the derived
--     values, so a receiver can recompute it from the row it holds and compare it with the server's.
--   * derived_fingerprint: AUTO_DAY only (null for MANUAL). sha256 of the canonical jsonb text of
--       {"parcels": [{"parcelId", "weightGrams", "allocationMode"} ... ordered by parcelId text], "weightGrams": n}
--     i.e. exactly what derive_auto_day recomputes from the live Pesadas (harvest_parcels ids are not covered: they
--     are md5 identifiers of the server). A receiver compares it with its own recomputation (WEIGHT_DIVERGENT), never
--     copies it. Derived changes never move the Jornada version, so the HEAD stays valid while they change.
--   * MANUAL split: not covered (the 12-key contract does not carry it; R1-b retains such Jornadas).
--   * Canonical jsonb text: keys ordered by length then bytes, ', ' and ': ' separators, arrays in their order.

CREATE FUNCTION public.get_own_harvest_head_v2(p_harvest_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID; h public.harvests%ROWTYPE; farm public.farms%ROWTYPE; candidates INTEGER;
    head magina_g2_private.sync_operations%ROWTYPE; auto BOOLEAN; own JSONB; derived JSONB;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_harvest_id IS NULL THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    SELECT * INTO h FROM public.harvests WHERE id=p_harvest_id;
    IF NOT FOUND OR NOT EXISTS (SELECT 1 FROM public.workspaces w
        WHERE w.id=h.workspace_id AND w.owner_user_id=actor AND w.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT * INTO farm FROM public.farms WHERE id=h.farm_id AND workspace_id=h.workspace_id;
    IF NOT FOUND OR farm.deleted_at IS NOT NULL OR farm.status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'sync denied: FARM_NOT_LIVE' USING ERRCODE='42501';
    END IF;
    SELECT count(*) INTO candidates FROM magina_g2_private.sync_operations o
        WHERE o.account_id=actor AND o.entity_type='HARVEST' AND o.entity_id=h.id
          AND o.result_version=h.version AND o.status='APPLIED';
    IF candidates=1 THEN
        SELECT * INTO head FROM magina_g2_private.sync_operations o
            WHERE o.account_id=actor AND o.entity_type='HARVEST' AND o.entity_id=h.id
              AND o.result_version=h.version AND o.status='APPLIED';
    END IF;
    auto := h.day_origin IS NOT DISTINCT FROM 'AUTO_DAY';
    own := jsonb_build_object('id',h.id,'farmId',h.farm_id,'campaignId',h.campaign_id,'harvestDate',to_char(h.harvest_date,'YYYY-MM-DD'),
        'dayOrigin',CASE WHEN auto THEN 'AUTO_DAY' ELSE 'MANUAL' END,'weightGrams',CASE WHEN auto THEN NULL ELSE h.weight_grams END,
        'destination',h.destination,'notes',h.notes,'collectionMethod',h.collection_method,'workerCount',h.worker_count,
        'machineryText',h.machinery_text);
    IF auto THEN
        derived := jsonb_build_object('weightGrams',h.weight_grams,'parcels',coalesce((SELECT jsonb_agg(jsonb_build_object(
                'parcelId',hp.parcel_id,'weightGrams',hp.weight_grams,'allocationMode',hp.allocation_mode) ORDER BY hp.parcel_id::text)
            FROM public.harvest_parcels hp WHERE hp.harvest_id=h.id),'[]'::jsonb));
    END IF;
    RETURN jsonb_build_object('contract_version',2,'fingerprint_version',1,'harvest_id',h.id,'version',h.version,
        'day_origin',h.day_origin,'deleted',h.deleted_at IS NOT NULL,'weight_grams',h.weight_grams,
        'head_operation_id',head.operation_id,'head_payload_version',head.payload_version,'head_payload_hash',head.payload_hash,
        'own_fingerprint',encode(sha256(convert_to(own::text,'UTF8')),'hex'),
        'derived_fingerprint',CASE WHEN auto THEN encode(sha256(convert_to(derived::text,'UTF8')),'hex') END);
END $$;
REVOKE ALL ON FUNCTION public.get_own_harvest_head_v2(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_harvest_head_v2(UUID) TO authenticated;

-- Self-check: an authenticated-only read.
DO $$
BEGIN
    IF has_function_privilege('anon','public.get_own_harvest_head_v2(uuid)','EXECUTE')
       OR has_function_privilege('public','public.get_own_harvest_head_v2(uuid)','EXECUTE')
       OR NOT has_function_privilege('authenticated','public.get_own_harvest_head_v2(uuid)','EXECUTE') THEN
        RAISE EXCEPTION 'get_own_harvest_head_v2 privileges are not exactly authenticated-only';
    END IF;
END $$;
