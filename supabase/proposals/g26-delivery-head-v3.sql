-- G2.6 #808: get_own_delivery_head_v3, the strict HEAD read a v3 (and v2) Pesada ACK needs.
-- Proposal only: disposable Supabase. NEVER apply to DEV or production without a separate decision.
-- Load after g26-historical-writers-v1.sql (uses magina_g2_private.historical_actor).
-- Design: docs/07-plans/G2.6-808-HEAD-V3-SQL-PROPOSAL-2026-11-02.md (#819, owner GO on #819).
--
-- Additive and read-only. get_own_delivery_v2, every writer, check_sync_operations, tables, RLS and
-- grants are NOT touched.
--
-- Contract: the head of the CURRENT version of one Pesada, as seen by its current workspace owner.
-- head_* come from the ONE applied DELIVERY receipt of THIS actor for this Pesada whose result_version
-- is the current version and whose payload_version is 2 or 3. Zero or several candidates (an
-- out-of-band write, a v1 header write, another actor's write, a duplicate) give null head_* fields:
-- the client must keep the operation unconfirmed. Nothing of other actors is ever returned.
CREATE FUNCTION public.get_own_delivery_head_v3(p_delivery_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID; d public.deliveries%ROWTYPE; farm public.farms%ROWTYPE; candidates INTEGER;
    head magina_g2_private.sync_operations%ROWTYPE;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_delivery_id IS NULL THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    -- Unknown, foreign and not-owned Pesadas look the same.
    SELECT * INTO d FROM public.deliveries WHERE id=p_delivery_id;
    IF NOT FOUND OR NOT EXISTS (SELECT 1 FROM public.workspaces w
        WHERE w.id=d.workspace_id AND w.owner_user_id=actor AND w.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT * INTO farm FROM public.farms WHERE id=d.farm_id AND workspace_id=d.workspace_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    IF farm.deleted_at IS NOT NULL OR farm.status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'sync denied: FARM_NOT_LIVE' USING ERRCODE='42501';
    END IF;
    IF d.deleted_at IS NULL THEN
        SELECT count(*) INTO candidates FROM magina_g2_private.sync_operations o
            WHERE o.account_id=actor AND o.entity_type='DELIVERY' AND o.entity_id=d.id
              AND o.result_version=d.version AND o.status='APPLIED' AND o.payload_version IN (2, 3);
        IF candidates=1 THEN
            SELECT * INTO head FROM magina_g2_private.sync_operations o
                WHERE o.account_id=actor AND o.entity_type='DELIVERY' AND o.entity_id=d.id
                  AND o.result_version=d.version AND o.status='APPLIED' AND o.payload_version IN (2, 3);
        END IF;
    END IF;
    RETURN jsonb_build_object(
        'contract_version', 1,
        'delivery_id', d.id,
        'version', d.version,
        'snapshot_format', d.snapshot_format,
        'deleted', d.deleted_at IS NOT NULL,
        'head_operation_id', head.operation_id,
        'head_payload_version', head.payload_version,
        'head_payload_hash', head.payload_hash);
END $$;
REVOKE ALL ON FUNCTION public.get_own_delivery_head_v3(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_delivery_head_v3(UUID) TO authenticated;

DO $$
BEGIN
    IF has_function_privilege('anon', 'public.get_own_delivery_head_v3(uuid)', 'EXECUTE')
       OR has_function_privilege('public', 'public.get_own_delivery_head_v3(uuid)', 'EXECUTE')
       OR NOT has_function_privilege('authenticated', 'public.get_own_delivery_head_v3(uuid)', 'EXECUTE') THEN
        RAISE EXCEPTION 'get_own_delivery_head_v3 privileges are not exactly authenticated-only';
    END IF;
END $$;
