-- G2.6 #808 R1-a: logical delete of a Pesada (delete_own_delivery_v1) and its strict delete HEAD.
-- Proposal only: disposable Supabase. NEVER apply to DEV or production without a separate decision.
-- Load after g26-historical-writers-v1.sql (historical_actor, lock_historical_scope), g26-delivery-head-v3.sql
-- and g26-harvest-writer-v1.sql (trg_deliveries_derive_harvest recomputes the AUTO_DAY).
-- Design: docs/07-plans/G2.6-808-RECEIVER-DESIGN-2026-11-03.md (H1, gate R1-a); owner GO on #828.
--
-- Additive. No table, column, constraint, RLS policy, grant or existing function is changed:
-- sync_own_delivery[_v2|_v3], get_own_delivery_v2, get_own_delivery_head_v3, check_sync_operations and the
-- Jornada writers keep their exact definitions.
--
-- Contract of delete_own_delivery_v1(p_operation_id, p_delivery_id, p_expected_version):
--   * Identity: the historical_actor() JWT rules (authenticated, literal issuer, unexpired, not anonymous).
--   * Scope: the actor owns the Pesada's workspace now, and its farm is live (lock_historical_scope).
--     Unknown, foreign and not-owned Pesadas look the same: 'sync denied: SCOPE'.
--   * CAS: p_expected_version must be the current version, else 'sync conflict: STALE_VERSION' with
--     DETAIL remote_version=<v>. An already deleted Pesada: 'sync conflict: DELETED'.
--   * Logical only: deleted_at is set (trg_deliveries_immutability bumps version and updated_at). The row,
--     its delivery_parcels, its fact_origins and every receipt stay; a hard delete stays forbidden.
--   * The AUTO_DAY of the Pesada is recomputed in the SAME transaction by trg_deliveries_derive_harvest
--     (kilos and harvest_parcels from the remaining live Pesadas; 0 and none after the last one). A MANUAL
--     day keeps its typed kilos. The Jornada version never moves (derived kilos are not a client payload).
--   * Receipt: one APPLIED DELIVERY receipt of this actor, result_version = the deleted version,
--     payload_version 3 (the v3 contract family), payload_hash = sha256 of the canonical jsonb text of
--     {"kind":"DELETE","deliveryId":<uuid>,"expectedVersion":<n>}. A delete is terminal, so the receipt
--     at a deleted version is always the delete.
--   * Idempotent: the same operation UUID with the same delivery and expected version replays its receipt
--     (idempotent=true, no second effect); with anything else: 'sync conflict: RECEIPT_CONFLICT'.
--
-- get_own_delivery_head_v3 is unchanged: for a deleted Pesada it reports deleted=true and null head_*,
-- so a held v3 WRITE is never confirmed by a delete. The delete itself is confirmed by
-- get_own_delivery_delete_head_v1, with the same strictness: the ONE applied receipt of THIS actor at
-- the current (deleted) version with payload_version 3, or all head_* null.

CREATE FUNCTION magina_g2_private.delete_delivery(p_operation_id UUID, p_delivery_id UUID, p_expected_version BIGINT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID; payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE;
    existing public.deliveries%ROWTYPE; ws UUID; tz TEXT; result_version BIGINT;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_operation_id IS NULL OR p_delivery_id IS NULL OR p_expected_version IS NULL OR p_expected_version<1 THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    payload_hash := encode(sha256(convert_to(jsonb_build_object('kind','DELETE','deliveryId',p_delivery_id,
        'expectedVersion',p_expected_version)::text,'UTF8')),'hex');
    -- The same operation UUID is decided once, even under concurrent retries.
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'DELIVERY' OR saved.entity_id<>p_delivery_id OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'sync conflict: RECEIPT_CONFLICT' USING ERRCODE='23505';
        END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',saved.entity_id,'status',saved.status,
            'remote_version',saved.result_version,'payload_version',saved.payload_version,'deleted',true,'idempotent',true);
    END IF;
    SELECT * INTO existing FROM public.deliveries WHERE id=p_delivery_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    -- Owner of the workspace NOW, farm live; workspace and farm locked FOR SHARE (same order as the writers).
    SELECT l.ws, l.tz INTO ws, tz FROM magina_g2_private.lock_historical_scope(actor, existing.farm_id) l;
    -- Pesada before Jornada, as sync_own_delivery_v3 does: the derivation trigger then locks the Jornada.
    SELECT * INTO existing FROM public.deliveries WHERE id=p_delivery_id AND workspace_id=ws FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    IF existing.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'sync conflict: DELETED' USING ERRCODE='23505';
    END IF;
    IF existing.version<>p_expected_version THEN
        RAISE EXCEPTION 'sync conflict: STALE_VERSION' USING ERRCODE='23505', DETAIL='remote_version='||existing.version;
    END IF;
    UPDATE public.deliveries SET deleted_at=now()
        WHERE id=p_delivery_id AND workspace_id=ws AND version=p_expected_version AND deleted_at IS NULL;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync conflict: CONCURRENT_UPDATE' USING ERRCODE='23505';
    END IF;
    SELECT version INTO result_version FROM public.deliveries WHERE id=p_delivery_id;
    INSERT INTO magina_g2_private.sync_operations(account_id, operation_id, entity_type, entity_id, payload_hash, status,
        result_version, payload_version)
    VALUES (actor, p_operation_id, 'DELIVERY', p_delivery_id, payload_hash, 'APPLIED', result_version, 3) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',p_delivery_id,'status',saved.status,
        'remote_version',saved.result_version,'payload_version',3,'deleted',true,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.delete_delivery(UUID,UUID,BIGINT) FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.delete_own_delivery_v1(p_operation_id UUID, p_delivery_id UUID, p_expected_version BIGINT)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.delete_delivery(p_operation_id, p_delivery_id, p_expected_version);
$$;
REVOKE ALL ON FUNCTION public.delete_own_delivery_v1(UUID,UUID,BIGINT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.delete_own_delivery_v1(UUID,UUID,BIGINT) TO authenticated;

-- Strict HEAD of a deleted Pesada, for its current workspace owner. Same scope rules as
-- get_own_delivery_head_v3. head_* name the ONE applied DELIVERY receipt of THIS actor at the current
-- version with payload_version 3, only when the Pesada is deleted; otherwise all null.
CREATE FUNCTION public.get_own_delivery_delete_head_v1(p_delivery_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID; d public.deliveries%ROWTYPE; farm public.farms%ROWTYPE; candidates INTEGER;
    head magina_g2_private.sync_operations%ROWTYPE;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_delivery_id IS NULL THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
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
    IF d.deleted_at IS NOT NULL THEN
        SELECT count(*) INTO candidates FROM magina_g2_private.sync_operations o
            WHERE o.account_id=actor AND o.entity_type='DELIVERY' AND o.entity_id=d.id
              AND o.result_version=d.version AND o.status='APPLIED' AND o.payload_version=3;
        IF candidates=1 THEN
            SELECT * INTO head FROM magina_g2_private.sync_operations o
                WHERE o.account_id=actor AND o.entity_type='DELIVERY' AND o.entity_id=d.id
                  AND o.result_version=d.version AND o.status='APPLIED' AND o.payload_version=3;
        END IF;
    END IF;
    RETURN jsonb_build_object(
        'contract_version', 1,
        'delivery_id', d.id,
        'version', d.version,
        'deleted', d.deleted_at IS NOT NULL,
        'head_operation_id', head.operation_id,
        'head_payload_version', head.payload_version,
        'head_payload_hash', head.payload_hash);
END $$;
REVOKE ALL ON FUNCTION public.get_own_delivery_delete_head_v1(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_delivery_delete_head_v1(UUID) TO authenticated;

-- Self-check: exactly authenticated-only endpoints and a closed private core.
DO $$
DECLARE fn TEXT;
BEGIN
    FOREACH fn IN ARRAY ARRAY['public.delete_own_delivery_v1(uuid,uuid,bigint)','public.get_own_delivery_delete_head_v1(uuid)'] LOOP
        IF has_function_privilege('anon', fn, 'EXECUTE') OR has_function_privilege('public', fn, 'EXECUTE')
           OR NOT has_function_privilege('authenticated', fn, 'EXECUTE') THEN
            RAISE EXCEPTION '% privileges are not exactly authenticated-only', fn;
        END IF;
    END LOOP;
    IF has_function_privilege('anon', 'magina_g2_private.delete_delivery(uuid,uuid,bigint)', 'EXECUTE')
       OR has_function_privilege('authenticated', 'magina_g2_private.delete_delivery(uuid,uuid,bigint)', 'EXECUTE') THEN
        RAISE EXCEPTION 'magina_g2_private.delete_delivery must not be executable by clients';
    END IF;
END $$;
