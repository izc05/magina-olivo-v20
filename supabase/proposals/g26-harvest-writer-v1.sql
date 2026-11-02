-- G2.6 #808 Jornadas (HARVEST): sync_own_harvest_v1, delete_own_harvest_v1, get_own_harvest_head_v1,
-- canonical AUTO_DAY identity and AUTO_DAY kilos/parcels derived by the server from its Pesadas.
-- Proposal only: disposable Supabase. NEVER apply to DEV or production without a separate decision.
-- Load after g26-historical-writers-v1.sql (historical_actor, lock_historical_scope, local_day_key).
-- Design: docs/07-plans/G2.6-808-HARVEST-SENDER-DESIGN-2026-11-02.md (#821), owner GO B (H2, H3a, H4).
--
-- The G0 RLS paths on public.harvests are NOT widened and NOT used; no grant changes.

-- H2. One live AUTO_DAY per (farm, campaign, date). The campaign is mandatory for an AUTO_DAY, so a
-- NULL campaign can never slip two of them past the unique index. MANUAL days are never deduplicated.
ALTER TABLE public.harvests ADD CONSTRAINT harvests_auto_day_campaign_required
    CHECK (day_origin IS DISTINCT FROM 'AUTO_DAY' OR campaign_id IS NOT NULL);
ALTER TABLE public.harvests ADD CONSTRAINT harvests_day_origin_known
    CHECK (day_origin IS NULL OR day_origin IN ('AUTO_DAY', 'MANUAL'));
CREATE UNIQUE INDEX harvests_auto_day_identity ON public.harvests (farm_id, campaign_id, harvest_date)
    WHERE day_origin = 'AUTO_DAY' AND deleted_at IS NULL;

-- H4. The kilos and parcel allocations of an AUTO_DAY are the exact derivation of ALL its live Pesadas:
-- weight = sum(net_grams); per parcel EXACT = sum when every share is EXACT, UNALLOCATED (NULL, never 0)
-- as soon as one share is not distributed. Recomputed in full, so a retry, an update, a logical delete
-- or a second device can never count twice. The Jornada row is locked FOR UPDATE first, so concurrent
-- Pesada writes serialize on it and each recomputation sees every committed Pesada. A Pesada can never
-- be attached to a deleted Jornada. MANUAL days keep their own kilos (no derivation).
CREATE FUNCTION magina_g2_private.derive_auto_day(p_harvest UUID) RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE day public.harvests%ROWTYPE; total NUMERIC;
BEGIN
    IF p_harvest IS NULL THEN RETURN; END IF;
    SELECT * INTO day FROM public.harvests WHERE id=p_harvest FOR UPDATE;
    IF NOT FOUND OR day.day_origin IS DISTINCT FROM 'AUTO_DAY' THEN RETURN; END IF;
    SELECT coalesce(sum(net_grams),0) INTO total FROM public.deliveries WHERE harvest_id=p_harvest AND deleted_at IS NULL;
    IF total>9223372036854775807 THEN
        RAISE EXCEPTION 'harvest invalid: WEIGHT_OVERFLOW' USING ERRCODE='22003';
    END IF;
    UPDATE public.harvests SET weight_grams=total::bigint WHERE id=p_harvest AND weight_grams IS DISTINCT FROM total::bigint;
    DELETE FROM public.harvest_parcels WHERE harvest_id=p_harvest;
    INSERT INTO public.harvest_parcels(id, workspace_id, harvest_id, parcel_id, campaign_parcel_id, parcel_name_at_harvest,
        weight_grams, allocation_mode)
    SELECT md5(p_harvest::text||':'||s.parcel_id::text)::uuid, day.workspace_id, p_harvest, s.parcel_id,
        (array_agg(s.campaign_parcel_id ORDER BY d.created_at, d.id))[1],
        (array_agg(s.parcel_name_at_delivery ORDER BY d.created_at, d.id))[1],
        CASE WHEN bool_and(s.allocation_mode='EXACT') THEN sum(s.weight_grams)::bigint END,
        CASE WHEN bool_and(s.allocation_mode='EXACT') THEN 'EXACT' ELSE 'UNALLOCATED' END
    FROM public.delivery_parcels s JOIN public.deliveries d ON d.id=s.delivery_id
    WHERE d.harvest_id=p_harvest AND d.deleted_at IS NULL
    GROUP BY s.parcel_id
    HAVING NOT bool_and(s.allocation_mode='EXACT') OR sum(s.weight_grams)<=9223372036854775807;
    IF EXISTS (SELECT 1 FROM public.delivery_parcels s JOIN public.deliveries d ON d.id=s.delivery_id
        WHERE d.harvest_id=p_harvest AND d.deleted_at IS NULL GROUP BY s.parcel_id
        HAVING bool_and(s.allocation_mode='EXACT') AND sum(s.weight_grams)>9223372036854775807) THEN
        RAISE EXCEPTION 'harvest invalid: WEIGHT_OVERFLOW' USING ERRCODE='22003';
    END IF;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.derive_auto_day(UUID) FROM PUBLIC, anon, authenticated;

CREATE FUNCTION magina_g2_private.deliveries_derive_harvest() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE old_h UUID; new_h UUID; new_live BOOLEAN := false;
BEGIN
    IF TG_OP IN ('UPDATE','DELETE') THEN old_h := OLD.harvest_id; END IF;
    IF TG_OP IN ('INSERT','UPDATE') THEN
        new_h := NEW.harvest_id;
        new_live := NEW.deleted_at IS NULL;
    END IF;
    IF new_h IS NOT NULL AND new_live THEN
        PERFORM 1 FROM public.harvests WHERE id=new_h FOR UPDATE;
        IF EXISTS (SELECT 1 FROM public.harvests WHERE id=new_h AND deleted_at IS NOT NULL) THEN
            RAISE EXCEPTION 'harvest conflict: HARVEST_DELETED' USING ERRCODE='23514';
        END IF;
    END IF;
    -- Two Jornadas are always locked in id order, so moves in opposite directions cannot deadlock.
    IF old_h IS NOT NULL AND new_h IS NOT NULL AND old_h<>new_h THEN
        PERFORM magina_g2_private.derive_auto_day(least(old_h,new_h));
        PERFORM magina_g2_private.derive_auto_day(greatest(old_h,new_h));
    ELSE
        PERFORM magina_g2_private.derive_auto_day(coalesce(new_h,old_h));
    END IF;
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.deliveries_derive_harvest() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER trg_deliveries_derive_harvest AFTER INSERT OR UPDATE OR DELETE ON public.deliveries
    FOR EACH ROW EXECUTE FUNCTION magina_g2_private.deliveries_derive_harvest();

CREATE FUNCTION magina_g2_private.delivery_parcels_derive_harvest() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE target UUID;
BEGIN
    IF TG_OP='DELETE' THEN target := OLD.delivery_id; ELSE target := NEW.delivery_id; END IF;
    PERFORM magina_g2_private.derive_auto_day((SELECT harvest_id FROM public.deliveries WHERE id=target));
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.delivery_parcels_derive_harvest() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER trg_delivery_parcels_derive_harvest AFTER INSERT OR UPDATE OR DELETE ON public.delivery_parcels
    FOR EACH ROW EXECUTE FUNCTION magina_g2_private.delivery_parcels_derive_harvest();

-- H3(a). Jornada writer: CREATE or UPDATE (baseVersion). Closed 12-key payload, all keys present.
CREATE FUNCTION magina_g2_private.sync_harvest(p_operation_id UUID, p_harvest JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID; payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE;
    v_id UUID; v_farm UUID; v_campaign UUID; v_date DATE; v_origin TEXT; v_weight BIGINT; v_workers INTEGER; v_base BIGINT;
    ws UUID; tz TEXT; camp public.campaigns%ROWTYPE; existing public.harvests%ROWTYPE; canonical UUID; result_version BIGINT;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_operation_id IS NULL OR p_harvest IS NULL OR jsonb_typeof(p_harvest) IS DISTINCT FROM 'object'
       OR octet_length(p_harvest::text)>65536 THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    IF (SELECT count(*) FROM jsonb_object_keys(p_harvest))<>12
       OR NOT p_harvest ?& ARRAY['id','farmId','campaignId','harvestDate','dayOrigin','weightGrams','destination','notes',
           'collectionMethod','workerCount','machineryText','baseVersion']
       OR EXISTS (SELECT 1 FROM jsonb_each(p_harvest) f(key,value)
           WHERE key IN ('id','farmId','harvestDate','dayOrigin') AND jsonb_typeof(value) IS DISTINCT FROM 'string')
       OR EXISTS (SELECT 1 FROM jsonb_each(p_harvest) f(key,value)
           WHERE key IN ('campaignId','destination','notes','collectionMethod','machineryText') AND jsonb_typeof(value) NOT IN ('string','null'))
       OR EXISTS (SELECT 1 FROM jsonb_each(p_harvest) f(key,value)
           WHERE key IN ('weightGrams','workerCount','baseVersion') AND jsonb_typeof(value)<>'null'
             AND (jsonb_typeof(value)<>'number' OR value::text !~ '^[0-9]{1,18}$'))
       OR p_harvest->>'harvestDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
       OR p_harvest->>'dayOrigin' NOT IN ('AUTO_DAY','MANUAL')
       OR length(p_harvest->>'destination')>512 OR length(p_harvest->>'notes')>10000
       OR length(p_harvest->>'collectionMethod')>100 OR length(p_harvest->>'machineryText')>2000 THEN
        RAISE EXCEPTION 'sync invalid: SHAPE' USING ERRCODE='22023';
    END IF;
    BEGIN
        v_id := (p_harvest->>'id')::uuid; v_farm := (p_harvest->>'farmId')::uuid; v_campaign := (p_harvest->>'campaignId')::uuid;
        v_date := (p_harvest->>'harvestDate')::date; v_origin := p_harvest->>'dayOrigin';
        v_weight := (p_harvest->>'weightGrams')::bigint; v_workers := (p_harvest->>'workerCount')::integer;
        v_base := (p_harvest->>'baseVersion')::bigint;
    EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR invalid_datetime_format OR datetime_field_overflow THEN
        RAISE EXCEPTION 'sync invalid: SHAPE' USING ERRCODE='22023';
    END;
    -- AUTO_DAY: campaign mandatory, kilos derived by the server (never sent). MANUAL: typed kilos.
    IF (v_origin='AUTO_DAY' AND (v_campaign IS NULL OR v_weight IS NOT NULL))
       OR (v_origin='MANUAL' AND v_weight IS NULL) OR (v_base IS NOT NULL AND v_base<1) THEN
        RAISE EXCEPTION 'sync invalid: DAY_ORIGIN_FIELDS' USING ERRCODE='22023';
    END IF;

    payload_hash := encode(sha256(convert_to(p_harvest::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'HARVEST' OR saved.entity_id<>v_id OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'sync conflict: RECEIPT_CONFLICT' USING ERRCODE='23505';
        END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',saved.entity_id,'status',saved.status,
            'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',true);
    END IF;

    -- Authority inside the transaction: owner of a live workspace, live ACTIVE farm, campaign of that farm.
    SELECT l.ws, l.tz INTO ws, tz FROM magina_g2_private.lock_historical_scope(actor, v_farm) l;
    IF v_campaign IS NOT NULL THEN
        SELECT * INTO camp FROM public.campaigns WHERE id=v_campaign AND farm_id=v_farm AND workspace_id=ws AND deleted_at IS NULL FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync invalid: CAMPAIGN_SCOPE' USING ERRCODE='22023';
        END IF;
    END IF;
    SELECT * INTO existing FROM public.harvests WHERE id=v_id FOR UPDATE;

    IF FOUND THEN
        IF existing.workspace_id<>ws THEN
            RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
        END IF;
        IF existing.deleted_at IS NOT NULL THEN
            RAISE EXCEPTION 'sync conflict: DELETED' USING ERRCODE='23505';
        END IF;
        -- The identity of a Jornada never changes: farm, campaign, date and origin.
        IF existing.farm_id<>v_farm OR existing.campaign_id IS DISTINCT FROM v_campaign OR existing.harvest_date<>v_date
           OR existing.day_origin IS DISTINCT FROM v_origin THEN
            RAISE EXCEPTION 'sync conflict: BINDING_IMMUTABLE' USING ERRCODE='23505';
        END IF;
        IF v_base IS NULL THEN
            RAISE EXCEPTION 'sync conflict: BASE_VERSION_REQUIRED' USING ERRCODE='23505';
        END IF;
        IF existing.version<>v_base THEN
            RAISE EXCEPTION 'sync conflict: STALE_VERSION' USING ERRCODE='23505', DETAIL='remote_version='||existing.version;
        END IF;
        -- A MANUAL day's typed kilos stop being editable once Pesadas hang from it (Phase 19B).
        IF v_origin='MANUAL' AND v_weight<>existing.weight_grams
           AND EXISTS (SELECT 1 FROM public.deliveries WHERE harvest_id=v_id AND deleted_at IS NULL) THEN
            RAISE EXCEPTION 'sync conflict: MANUAL_WEIGHT_HAS_PESADAS' USING ERRCODE='23505';
        END IF;
        UPDATE public.harvests SET destination=p_harvest->>'destination', notes=p_harvest->>'notes',
            collection_method=p_harvest->>'collectionMethod', worker_count=v_workers, machinery_text=p_harvest->>'machineryText',
            weight_grams=CASE WHEN v_origin='MANUAL' THEN v_weight ELSE weight_grams END,
            version=version+1, updated_at=now()
        WHERE id=v_id AND version=existing.version;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync conflict: CONCURRENT_UPDATE' USING ERRCODE='23505';
        END IF;
    ELSE
        IF v_date>(now() AT TIME ZONE tz)::date THEN
            RAISE EXCEPTION 'sync review: FUTURE_DATE' USING ERRCODE='22023';
        END IF;
        IF v_campaign IS NOT NULL AND (camp.start_date>v_date OR (camp.end_date IS NOT NULL AND camp.end_date<v_date)) THEN
            RAISE EXCEPTION 'sync review: CAMPAIGN_PERIOD' USING ERRCODE='22023';
        END IF;
        IF v_origin='AUTO_DAY' THEN
            -- H2: one canonical AUTO_DAY. The second device learns the canonical id (only the owner gets here)
            -- and must rebase its own unsent Pesadas; nothing is merged or reassigned on the server.
            PERFORM pg_advisory_xact_lock(hashtextextended('harvest-auto-day:'||v_farm::text||':'||v_campaign::text||':'||v_date::text,0));
            SELECT id INTO canonical FROM public.harvests WHERE farm_id=v_farm AND campaign_id=v_campaign AND harvest_date=v_date
                AND day_origin='AUTO_DAY' AND deleted_at IS NULL;
            IF FOUND THEN
                RAISE EXCEPTION 'sync conflict: AUTO_DAY_EXISTS' USING ERRCODE='23505', DETAIL='harvest_id='||canonical;
            END IF;
        END IF;
        INSERT INTO public.harvests(id, workspace_id, farm_id, campaign_id, harvest_date, weight_grams, destination, notes,
            collection_method, worker_count, machinery_text, day_origin, recorded_by_user_id, version)
        VALUES (v_id, ws, v_farm, v_campaign, v_date, CASE WHEN v_origin='MANUAL' THEN v_weight ELSE 0 END,
            p_harvest->>'destination', p_harvest->>'notes', p_harvest->>'collectionMethod', v_workers,
            p_harvest->>'machineryText', v_origin, actor, 1);
    END IF;

    SELECT version INTO result_version FROM public.harvests WHERE id=v_id;
    INSERT INTO magina_g2_private.sync_operations(account_id, operation_id, entity_type, entity_id, payload_hash, status,
        result_version, payload_version)
    VALUES (actor, p_operation_id, 'HARVEST', v_id, payload_hash, 'APPLIED', result_version, 1) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',v_id,'status',saved.status,
        'remote_version',saved.result_version,'payload_version',1,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.sync_harvest(UUID,JSONB) FROM PUBLIC, anon, authenticated;

-- H3(a) / H8: logical delete only, refused while anything live hangs from the Jornada.
CREATE FUNCTION magina_g2_private.delete_harvest(p_operation_id UUID, p_harvest_id UUID, p_expected_version BIGINT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID; payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE; existing public.harvests%ROWTYPE;
    ws UUID; tz TEXT; result_version BIGINT;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_operation_id IS NULL OR p_harvest_id IS NULL OR p_expected_version IS NULL OR p_expected_version<1 THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    payload_hash := encode(sha256(convert_to(jsonb_build_object('kind','DELETE','harvestId',p_harvest_id,
        'expectedVersion',p_expected_version)::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'HARVEST' OR saved.entity_id<>p_harvest_id OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'sync conflict: RECEIPT_CONFLICT' USING ERRCODE='23505';
        END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',saved.entity_id,'status',saved.status,
            'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',true);
    END IF;
    SELECT * INTO existing FROM public.harvests WHERE id=p_harvest_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT l.ws, l.tz INTO ws, tz FROM magina_g2_private.lock_historical_scope(actor, existing.farm_id) l;
    SELECT * INTO existing FROM public.harvests WHERE id=p_harvest_id AND workspace_id=ws FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    IF existing.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'sync conflict: DELETED' USING ERRCODE='23505';
    END IF;
    IF existing.version<>p_expected_version THEN
        RAISE EXCEPTION 'sync conflict: STALE_VERSION' USING ERRCODE='23505', DETAIL='remote_version='||existing.version;
    END IF;
    -- Server expenses have no harvest link yet; the Pesadas are the live dependents on the server.
    IF EXISTS (SELECT 1 FROM public.deliveries WHERE harvest_id=p_harvest_id AND deleted_at IS NULL) THEN
        RAISE EXCEPTION 'sync conflict: HARVEST_HAS_DEPENDENTS' USING ERRCODE='23505';
    END IF;
    UPDATE public.harvests SET deleted_at=now(), version=version+1, updated_at=now() WHERE id=p_harvest_id;
    DELETE FROM public.harvest_parcels WHERE harvest_id=p_harvest_id;
    SELECT version INTO result_version FROM public.harvests WHERE id=p_harvest_id;
    INSERT INTO magina_g2_private.sync_operations(account_id, operation_id, entity_type, entity_id, payload_hash, status,
        result_version, payload_version)
    VALUES (actor, p_operation_id, 'HARVEST', p_harvest_id, payload_hash, 'APPLIED', result_version, 1) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',p_harvest_id,'status',saved.status,
        'remote_version',saved.result_version,'payload_version',1,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.delete_harvest(UUID,UUID,BIGINT) FROM PUBLIC, anon, authenticated;

-- Strict HEAD of the CURRENT version of a Jornada, for its current workspace owner. head_* name the single
-- applied HARVEST receipt (create, update or delete) of this actor at that version, or are all null. Derived kilos are
-- reported but never move the version: they are not part of any client payload.
CREATE FUNCTION public.get_own_harvest_head_v1(p_harvest_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID; h public.harvests%ROWTYPE; farm public.farms%ROWTYPE; candidates INTEGER;
    head magina_g2_private.sync_operations%ROWTYPE;
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
    RETURN jsonb_build_object('contract_version',1,'harvest_id',h.id,'version',h.version,'day_origin',h.day_origin,
        'deleted',h.deleted_at IS NOT NULL,'weight_grams',h.weight_grams,
        'head_operation_id',head.operation_id,'head_payload_version',head.payload_version,'head_payload_hash',head.payload_hash);
END $$;

CREATE FUNCTION public.sync_own_harvest_v1(p_operation_id UUID, p_harvest JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.sync_harvest(p_operation_id, p_harvest);
$$;
CREATE FUNCTION public.delete_own_harvest_v1(p_operation_id UUID, p_harvest_id UUID, p_expected_version BIGINT)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.delete_harvest(p_operation_id, p_harvest_id, p_expected_version);
$$;
REVOKE ALL ON FUNCTION public.sync_own_harvest_v1(UUID,JSONB) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.delete_own_harvest_v1(UUID,UUID,BIGINT) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_own_harvest_head_v1(UUID) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.sync_own_harvest_v1(UUID,JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_own_harvest_v1(UUID,UUID,BIGINT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_harvest_head_v1(UUID) TO authenticated;

DO $$
DECLARE role_name TEXT; fn TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon','authenticated'] LOOP
        FOREACH fn IN ARRAY ARRAY['magina_g2_private.sync_harvest(uuid,jsonb)','magina_g2_private.delete_harvest(uuid,uuid,bigint)',
            'magina_g2_private.derive_auto_day(uuid)','magina_g2_private.deliveries_derive_harvest()',
            'magina_g2_private.delivery_parcels_derive_harvest()'] LOOP
            IF has_function_privilege(role_name, fn, 'EXECUTE') THEN
                RAISE EXCEPTION 'harvest helper % is executable by %', fn, role_name;
            END IF;
        END LOOP;
    END LOOP;
    FOREACH fn IN ARRAY ARRAY['public.sync_own_harvest_v1(uuid,jsonb)','public.delete_own_harvest_v1(uuid,uuid,bigint)',
        'public.get_own_harvest_head_v1(uuid)'] LOOP
        IF has_function_privilege('anon', fn, 'EXECUTE') OR has_function_privilege('public', fn, 'EXECUTE')
           OR NOT has_function_privilege('authenticated', fn, 'EXECUTE') THEN
            RAISE EXCEPTION 'harvest endpoint % privileges are not exactly authenticated-only', fn;
        END IF;
    END LOOP;
END $$;
