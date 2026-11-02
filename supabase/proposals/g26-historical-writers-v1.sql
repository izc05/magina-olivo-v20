-- G2.6 #808: historical Pesada and expense writers (sync_own_delivery_v3, sync_own_expense_v2).
-- Proposal only: disposable Supabase. NEVER apply to DEV or production without a separate deployment
-- decision and backup/restore plan. Design and decisions: docs/07-plans/G2.6-808-*.md (#817, owner Q1-Q5).
-- Load after g2-sync-own-data.sql, g2-parcel-bootstrap.sql, g26-fpm-manage-hardening.sql and
-- g26-parcel-mutation-v1.sql.
--
-- Additive only:
-- - sync_own_delivery, sync_own_delivery_v2, sync_own_expense and their private core are NOT touched;
-- - no public table, column, RLS policy or grant changes.
-- The client-declared creation instant (P3) lives in a private append-only table, never in a public row.
--
-- Rejections carry a stable token in the message: '<class>: <CAUSE>'.
--   sync denied   42501  authority or scope (never says whether a foreign row exists)
--   sync invalid  22023  malformed payload (a client bug)
--   sync review   22023 temporal/data, 42501 membership/archive: the client must KEEP the fact locally and
--                 surface it for explicit review; it is never discarded or overwritten
--   sync conflict 23505  receipt, version or frozen historical binding

-- 1. originCreatedAt (P3): one immutable row per fact, written only by these writers.
CREATE TABLE magina_g2_private.fact_origins (
    entity_type TEXT NOT NULL CHECK (entity_type IN ('DELIVERY','EXPENSE')),
    entity_id UUID NOT NULL,
    workspace_id UUID NOT NULL REFERENCES public.workspaces(id),
    origin_created_at_exact TEXT NOT NULL,
    -- true when attached on the first v3/v2 update of a row created by a legacy writer.
    adopted BOOLEAN NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (entity_type, entity_id)
);
ALTER TABLE magina_g2_private.fact_origins ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON magina_g2_private.fact_origins FROM PUBLIC, anon, authenticated;

CREATE FUNCTION magina_g2_private.fact_origins_immutable() RETURNS trigger
LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
    RAISE EXCEPTION 'fact origin is immutable';
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.fact_origins_immutable() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER trg_fact_origins_immutable BEFORE UPDATE OR DELETE ON magina_g2_private.fact_origins
    FOR EACH ROW EXECUTE FUNCTION magina_g2_private.fact_origins_immutable();

-- 2. Strict identity: exact issuer, authenticated role, matching UUID subject, not anonymous, live exp.
CREATE FUNCTION magina_g2_private.historical_actor() RETURNS UUID
LANGUAGE plpgsql STABLE SET search_path='' AS $$
DECLARE actor UUID := auth.uid(); claims JSONB := auth.jwt();
BEGIN
    IF actor IS NULL OR claims IS NULL
       OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR claims->>'role' IS DISTINCT FROM 'authenticated' OR claims->>'sub' IS DISTINCT FROM actor::text
       OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR (claims->>'exp') !~ '^[0-9]{1,12}$'
       OR (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'sync denied: IDENTITY' USING ERRCODE='42501';
    END IF;
    RETURN actor;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.historical_actor() FROM PUBLIC, anon, authenticated;

-- 3. A local civil time (date + HH:MM) in the workspace time zone has exactly one instant, or it is
-- rejected for review. Never guess: a repeated hour (DST end) or a skipped hour (DST start) cannot be
-- placed on the timeline from HH:MM alone. Every UTC offset in effect within a day of the probe is
-- tried; an offset counts only if it maps back to the same civil time.
CREATE FUNCTION magina_g2_private.resolve_civil_time(d DATE, hhmm TEXT, tz TEXT) RETURNS TIMESTAMPTZ
LANGUAGE plpgsql STABLE SET search_path='' AS $$
DECLARE
    local_ts TIMESTAMP := d + hhmm::time;
    probe TIMESTAMPTZ := (d + hhmm::time) AT TIME ZONE tz;
    shift INTERVAL; candidate TIMESTAMPTZ; matches TIMESTAMPTZ[] := '{}';
BEGIN
    FOREACH shift IN ARRAY ARRAY[interval '-1 day', interval '-12 hours', interval '0', interval '12 hours',
        interval '1 day'] LOOP
        candidate := (local_ts - (((probe+shift) AT TIME ZONE tz) - ((probe+shift) AT TIME ZONE 'UTC'))) AT TIME ZONE 'UTC';
        IF (candidate AT TIME ZONE tz) = local_ts AND NOT (candidate = ANY(matches)) THEN
            matches := array_append(matches, candidate);
        END IF;
    END LOOP;
    IF cardinality(matches)=0 THEN
        RAISE EXCEPTION 'sync review: DELIVERY_TIME_NONEXISTENT' USING ERRCODE='22023';
    ELSIF cardinality(matches)>1 THEN
        RAISE EXCEPTION 'sync review: DELIVERY_TIME_AMBIGUOUS' USING ERRCODE='22023';
    END IF;
    RETURN matches[1];
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.resolve_civil_time(DATE,TEXT,TEXT) FROM PUBLIC, anon, authenticated;

-- First instant of local day d in tz, as a nanosecond key. A skipped local midnight resolves to the
-- transition instant (PostgreSQL applies the offset in force before it), which is where that day starts.
CREATE FUNCTION magina_g2_private.local_day_key(d DATE, tz TEXT) RETURNS NUMERIC
LANGUAGE sql STABLE SET search_path='' AS $$
    SELECT extract(epoch FROM (d::timestamp AT TIME ZONE tz))*1000000000;
$$;
REVOKE ALL ON FUNCTION magina_g2_private.local_day_key(DATE,TEXT) FROM PUBLIC, anon, authenticated;

-- 4. Parcel state: coherent ACTIVE (no tombstone) -> NULL; coherent ARCHIVED (instant, no open period)
-- -> its exact archive key. Anything else is an incongruent tombstone and is never accepted.
CREATE FUNCTION magina_g2_private.historical_parcel_archive_key(p_parcel UUID, p_workspace UUID) RETURNS NUMERIC
LANGUAGE plpgsql STABLE SET search_path='' AS $$
DECLARE par public.parcels%ROWTYPE;
BEGIN
    SELECT * INTO par FROM public.parcels WHERE id=p_parcel AND workspace_id=p_workspace;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: PARCEL_SCOPE' USING ERRCODE='42501';
    END IF;
    IF par.status='ACTIVE' AND par.deleted_at IS NULL AND par.deleted_at_exact IS NULL THEN
        RETURN NULL;
    END IF;
    IF par.status='ARCHIVED' AND par.deleted_at IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM public.farm_parcel_memberships
        WHERE parcel_id=p_parcel AND deleted_at IS NULL AND valid_until IS NULL) THEN
        RETURN magina_g2_private.moment_key(par.deleted_at, par.deleted_at_exact);
    END IF;
    RAISE EXCEPTION 'sync review: PARCEL_INCOHERENT' USING ERRCODE='42501';
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.historical_parcel_archive_key(UUID,UUID) FROM PUBLIC, anon, authenticated;

-- 5. Historical membership of one parcel in the farm at the fact, on exact nanosecond keys.
-- A timed fact is the point t: from <= t < until. An untimed fact is the local day [from, to): any
-- overlap counts (P1). P3, when p_check_origin: relying on a period that has closed (an archive or a
-- move to another farm) requires origin + 5 min <= its end, and an archived parcel requires the same
-- against its archive instant A. A fact first recorded after that closure, or too close to it to rule
-- out clock skew, goes to review: it is neither written nor discarded.
CREATE FUNCTION magina_g2_private.check_historical_parcel(p_workspace UUID, p_farm UUID, p_parcel UUID,
    p_from NUMERIC, p_to NUMERIC, p_timed BOOLEAN, p_origin NUMERIC, p_check_origin BOOLEAN) RETURNS VOID
LANGUAGE plpgsql STABLE SET search_path='' AS $$
DECLARE
    margin CONSTANT NUMERIC := 300000000000;
    archived NUMERIC; period RECORD; seen BOOLEAN := false; covered BOOLEAN := false; near BOOLEAN := false;
BEGIN
    archived := magina_g2_private.historical_parcel_archive_key(p_parcel, p_workspace);
    IF archived IS NOT NULL AND p_from >= archived THEN
        RAISE EXCEPTION 'sync review: AFTER_ARCHIVE' USING ERRCODE='42501';
    END IF;
    FOR period IN
        SELECT magina_g2_private.moment_key(valid_from, valid_from_exact) AS lo,
               magina_g2_private.moment_key(valid_until, valid_until_exact) AS hi
        FROM public.farm_parcel_memberships
        WHERE parcel_id=p_parcel AND workspace_id=p_workspace AND farm_id=p_farm AND deleted_at IS NULL
    LOOP
        IF period.hi IS NOT NULL AND period.hi<=period.lo THEN CONTINUE; END IF;
        IF p_timed THEN
            IF NOT (period.lo<=p_from AND (period.hi IS NULL OR period.hi>p_from)) THEN CONTINUE; END IF;
        ELSE
            IF NOT (period.lo<p_to AND (period.hi IS NULL OR period.hi>p_from)) THEN CONTINUE; END IF;
        END IF;
        seen := true;
        IF NOT p_check_origin OR period.hi IS NULL OR p_origin+margin<=period.hi THEN
            covered := true;
            EXIT;
        END IF;
        near := near OR p_origin<period.hi;
    END LOOP;
    IF NOT seen THEN
        RAISE EXCEPTION 'sync review: NOT_MEMBER_ON_DATE' USING ERRCODE='42501';
    END IF;
    IF p_check_origin AND archived IS NOT NULL AND covered AND p_origin+margin>archived THEN
        covered := false;
        near := p_origin<archived;
    END IF;
    IF NOT covered THEN
        IF near THEN
            RAISE EXCEPTION 'sync review: ORIGIN_CLOCK_UNCERTAIN' USING ERRCODE='42501';
        END IF;
        RAISE EXCEPTION 'sync review: ORIGIN_NOT_BEFORE_CLOSURE' USING ERRCODE='42501';
    END IF;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.check_historical_parcel(UUID,UUID,UUID,NUMERIC,NUMERIC,BOOLEAN,NUMERIC,BOOLEAN)
    FROM PUBLIC, anon, authenticated;

-- 6. Owner of a live workspace, then its live ACTIVE farm, locked in this order (compatible with #805:
-- workspace FOR SHARE first; farm, parcels and periods only FOR SHARE here). Unknown and foreign farms
-- look the same. Returns the workspace time zone.
CREATE FUNCTION magina_g2_private.lock_historical_scope(p_actor UUID, p_farm UUID, OUT ws UUID, OUT tz TEXT)
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE farm public.farms%ROWTYPE;
BEGIN
    -- farms.workspace_id is immutable (check_farm_principal_immutability), so reading it first is safe.
    SELECT workspace_id INTO ws FROM public.farms WHERE id=p_farm;
    SELECT w.timezone INTO tz FROM public.workspaces w
        WHERE w.id=ws AND w.owner_user_id=p_actor AND w.deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    SELECT * INTO farm FROM public.farms WHERE id=p_farm AND workspace_id=ws FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
    END IF;
    IF farm.deleted_at IS NOT NULL OR farm.status IS DISTINCT FROM 'ACTIVE' THEN
        RAISE EXCEPTION 'sync denied: FARM_NOT_LIVE' USING ERRCODE='42501';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_timezone_names WHERE name=tz) THEN
        RAISE EXCEPTION 'sync invalid: WORKSPACE_TIMEZONE' USING ERRCODE='22023';
    END IF;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.lock_historical_scope(UUID,UUID) FROM PUBLIC, anon, authenticated;

-- 7. originCreatedAt: same instant grammar as the parcel contracts (UTC, up to nanoseconds).
CREATE FUNCTION magina_g2_private.origin_key(value TEXT) RETURNS NUMERIC
LANGUAGE plpgsql STABLE SET search_path='' AS $$
BEGIN
    IF value IS NULL OR value !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]([.][0-9]{1,9})?Z$' THEN
        RAISE EXCEPTION 'sync invalid: ORIGIN_CREATED_AT' USING ERRCODE='22023';
    END IF;
    RETURN magina_g2_private.instant_key(value);
EXCEPTION WHEN invalid_parameter_value OR datetime_field_overflow OR invalid_datetime_format THEN
    RAISE EXCEPTION 'sync invalid: ORIGIN_CREATED_AT' USING ERRCODE='22023';
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.origin_key(TEXT) FROM PUBLIC, anon, authenticated;

-- 8. Pesada v3. Payload: every v2 field plus originCreatedAt, all keys present (nullable ones may be null).
CREATE FUNCTION magina_g2_private.sync_delivery_historical(p_operation_id UUID, p_delivery JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    margin CONSTANT NUMERIC := 300000000000;
    actor UUID; payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE;
    v_id UUID; v_farm UUID; v_campaign UUID; v_date DATE; v_time TEXT; v_net BIGINT; v_gross BIGINT; v_tare BIGINT;
    v_dest_org UUID; v_harvest UUID; v_source TEXT; v_origin TEXT; v_origin_key NUMERIC; v_base BIGINT;
    item JSONB; v_parcel UUID; child_ids UUID[] := '{}'; parcel_ids UUID[] := '{}'; old_ids UUID[] := '{}';
    all_ids UUID[]; known_grams NUMERIC := 0; unknown_count INTEGER := 0; locked INTEGER;
    ws UUID; tz TEXT; camp public.campaigns%ROWTYPE; existing public.deliveries%ROWTYPE; is_update BOOLEAN;
    stored_origin TEXT; adopt BOOLEAN := false; unchanged BOOLEAN := false; archived_involved BOOLEAN;
    instant TIMESTAMPTZ; fact_from NUMERIC; fact_to NUMERIC; timed BOOLEAN; result_version BIGINT;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_operation_id IS NULL OR p_delivery IS NULL OR jsonb_typeof(p_delivery) IS DISTINCT FROM 'object'
       OR octet_length(p_delivery::text)>4194304 THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;

    -- Closed shape and types, before any lock.
    IF (SELECT count(*) FROM jsonb_object_keys(p_delivery))<>19
       OR NOT p_delivery ?& ARRAY['id','farmId','campaignId','deliveryDate','destinationName','destinationOrganizationId',
           'netGrams','grossGrams','tareGrams','deliveryNumber','ticketNumber','source','notes','harvestId',
           'deliveryTime','harvestOrigin','baseVersion','parcels','originCreatedAt']
       OR EXISTS (SELECT 1 FROM jsonb_each(p_delivery) f(key,value)
           WHERE key IN ('id','farmId','campaignId','deliveryDate','destinationName','source','originCreatedAt')
             AND jsonb_typeof(value) IS DISTINCT FROM 'string')
       OR EXISTS (SELECT 1 FROM jsonb_each(p_delivery) f(key,value)
           WHERE key IN ('destinationOrganizationId','deliveryNumber','ticketNumber','notes','harvestId','deliveryTime','harvestOrigin')
             AND jsonb_typeof(value) NOT IN ('string','null'))
       OR jsonb_typeof(p_delivery->'netGrams') IS DISTINCT FROM 'number' OR (p_delivery->>'netGrams') !~ '^[0-9]{1,18}$'
       OR EXISTS (SELECT 1 FROM jsonb_each(p_delivery) f(key,value)
           WHERE key IN ('grossGrams','tareGrams','baseVersion') AND jsonb_typeof(value)<>'null'
             AND (jsonb_typeof(value)<>'number' OR value::text !~ '^[0-9]{1,18}$'))
       OR jsonb_typeof(p_delivery->'parcels') IS DISTINCT FROM 'array'
       OR jsonb_array_length(p_delivery->'parcels') NOT BETWEEN 1 AND 1000
       OR p_delivery->>'deliveryDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
       OR (p_delivery->>'deliveryTime' IS NOT NULL AND p_delivery->>'deliveryTime' !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$')
       OR length(btrim(p_delivery->>'destinationName')) NOT BETWEEN 1 AND 512
       OR length(p_delivery->>'deliveryNumber')>200 OR length(p_delivery->>'ticketNumber')>200
       OR length(p_delivery->>'notes')>10000
       OR p_delivery->>'source' NOT IN ('MANUAL','TICKET_OCR')
       OR (p_delivery->>'harvestOrigin' IS NOT NULL AND p_delivery->>'harvestOrigin' NOT IN ('TREE','GROUND')) THEN
        RAISE EXCEPTION 'sync invalid: SHAPE' USING ERRCODE='22023';
    END IF;
    BEGIN
        v_id := (p_delivery->>'id')::uuid; v_farm := (p_delivery->>'farmId')::uuid;
        v_campaign := (p_delivery->>'campaignId')::uuid; v_date := (p_delivery->>'deliveryDate')::date;
        v_time := p_delivery->>'deliveryTime'; v_net := (p_delivery->>'netGrams')::bigint;
        v_gross := (p_delivery->>'grossGrams')::bigint; v_tare := (p_delivery->>'tareGrams')::bigint;
        v_dest_org := (p_delivery->>'destinationOrganizationId')::uuid; v_harvest := (p_delivery->>'harvestId')::uuid;
        v_base := (p_delivery->>'baseVersion')::bigint; v_source := p_delivery->>'source';
    EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR invalid_datetime_format
        OR datetime_field_overflow THEN
        RAISE EXCEPTION 'sync invalid: SHAPE' USING ERRCODE='22023';
    END;
    IF v_net<=0 OR (v_gross IS NOT NULL AND v_gross<=0) OR (v_tare IS NOT NULL AND v_tare<0)
       OR (v_gross IS NOT NULL AND v_tare IS NOT NULL AND v_gross::numeric-v_tare::numeric<>v_net)
       OR (v_base IS NOT NULL AND v_base<1) THEN
        RAISE EXCEPTION 'sync invalid: WEIGHTS' USING ERRCODE='22023';
    END IF;
    v_origin := p_delivery->>'originCreatedAt';
    v_origin_key := magina_g2_private.origin_key(v_origin);
    FOR item IN SELECT value FROM jsonb_array_elements(p_delivery->'parcels') LOOP
        IF jsonb_typeof(item) IS DISTINCT FROM 'object'
           OR (SELECT count(*) FROM jsonb_object_keys(item))<>6
           OR NOT item ?& ARRAY['id','parcelId','campaignParcelId','parcelNameAtDelivery','weightGrams','allocationMode']
           OR jsonb_typeof(item->'id') IS DISTINCT FROM 'string' OR jsonb_typeof(item->'parcelId') IS DISTINCT FROM 'string'
           OR jsonb_typeof(item->'parcelNameAtDelivery') IS DISTINCT FROM 'string'
           OR length(item->>'parcelNameAtDelivery') NOT BETWEEN 1 AND 512
           OR jsonb_typeof(item->'allocationMode') IS DISTINCT FROM 'string'
           OR jsonb_typeof(item->'campaignParcelId') NOT IN ('string','null') THEN
            RAISE EXCEPTION 'sync invalid: PARCEL_SHAPE' USING ERRCODE='22023';
        END IF;
        BEGIN
            v_parcel := (item->>'parcelId')::uuid;
            PERFORM (item->>'id')::uuid, (item->>'campaignParcelId')::uuid;
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'sync invalid: PARCEL_SHAPE' USING ERRCODE='22023';
        END;
        IF v_parcel=ANY(parcel_ids) OR (item->>'id')::uuid=ANY(child_ids) THEN
            RAISE EXCEPTION 'sync invalid: DUPLICATE_PARCEL' USING ERRCODE='22023';
        END IF;
        parcel_ids := array_append(parcel_ids, v_parcel);
        child_ids := array_append(child_ids, (item->>'id')::uuid);
        IF item->>'allocationMode'='UNALLOCATED' AND jsonb_typeof(item->'weightGrams')='null' THEN
            unknown_count := unknown_count+1;
        ELSIF item->>'allocationMode'='EXACT' AND jsonb_typeof(item->'weightGrams')='number'
           AND (item->>'weightGrams') ~ '^[0-9]{1,18}$' AND (item->>'weightGrams')::numeric>0 THEN
            known_grams := known_grams+(item->>'weightGrams')::numeric;
        ELSE
            RAISE EXCEPTION 'sync invalid: PARCEL_WEIGHT' USING ERRCODE='22023';
        END IF;
    END LOOP;
    IF known_grams>v_net OR (unknown_count=0 AND known_grams<>v_net) THEN
        RAISE EXCEPTION 'sync invalid: WEIGHTS' USING ERRCODE='22023';
    END IF;

    payload_hash := encode(sha256(convert_to(p_delivery::text,'UTF8')),'hex');
    -- Same key space as every G2 writer: one operation id is serialized whatever RPC carries it.
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'DELIVERY' OR saved.entity_id<>v_id OR saved.payload_hash<>payload_hash
           OR saved.payload_version<>3 THEN
            RAISE EXCEPTION 'sync conflict: RECEIPT_CONFLICT' USING ERRCODE='23505';
        END IF;
        -- Lost-response recovery (D5): the stored receipt of THIS actor, with no new write and no
        -- re-authorization, so a later revocation never turns an applied operation into a failure.
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',saved.entity_id,
            'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,
            'idempotent',true);
    END IF;

    -- Authority inside the transaction: workspace -> farm -> campaign -> parcels -> periods -> delivery.
    SELECT l.ws, l.tz INTO ws, tz FROM magina_g2_private.lock_historical_scope(actor, v_farm) l;
    SELECT * INTO camp FROM public.campaigns
        WHERE id=v_campaign AND farm_id=v_farm AND workspace_id=ws AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync invalid: CAMPAIGN_SCOPE' USING ERRCODE='22023';
    END IF;
    -- Parcels already referenced by this delivery are locked too; re-checked under the delivery lock.
    SELECT coalesce(array_agg(parcel_id),'{}') INTO old_ids FROM public.delivery_parcels WHERE delivery_id=v_id;
    all_ids := ARRAY(SELECT DISTINCT unnest(parcel_ids||old_ids));
    SELECT count(*) INTO locked FROM (SELECT id FROM public.parcels WHERE id=ANY(all_ids) AND workspace_id=ws
        ORDER BY id FOR SHARE) l;
    IF locked<>cardinality(all_ids) THEN
        RAISE EXCEPTION 'sync denied: PARCEL_SCOPE' USING ERRCODE='42501';
    END IF;
    PERFORM 1 FROM public.farm_parcel_memberships WHERE parcel_id=ANY(all_ids) AND workspace_id=ws
        ORDER BY id FOR SHARE;
    SELECT * INTO existing FROM public.deliveries WHERE id=v_id FOR UPDATE;
    is_update := FOUND;

    IF v_origin_key>extract(epoch FROM now())*1000000000+margin THEN
        RAISE EXCEPTION 'sync review: ORIGIN_IN_FUTURE' USING ERRCODE='22023';
    END IF;

    IF is_update THEN
        IF existing.workspace_id<>ws THEN
            RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
        END IF;
        IF existing.deleted_at IS NOT NULL THEN
            RAISE EXCEPTION 'sync conflict: DELETED' USING ERRCODE='23505';
        END IF;
        IF existing.farm_id<>v_farm OR existing.campaign_id<>v_campaign THEN
            RAISE EXCEPTION 'sync conflict: BINDING_IMMUTABLE' USING ERRCODE='23505';
        END IF;
        IF v_base IS NULL THEN
            RAISE EXCEPTION 'sync conflict: BASE_VERSION_REQUIRED' USING ERRCODE='23505';
        END IF;
        IF existing.version<>v_base THEN
            RAISE EXCEPTION 'sync conflict: STALE_VERSION' USING ERRCODE='23505', DETAIL='remote_version='||existing.version;
        END IF;
        IF NOT (SELECT coalesce(array_agg(parcel_id),'{}') FROM public.delivery_parcels WHERE delivery_id=v_id) <@ all_ids THEN
            RAISE EXCEPTION 'sync conflict: CONCURRENT_UPDATE' USING ERRCODE='23505';
        END IF;
        SELECT origin_created_at_exact INTO stored_origin FROM magina_g2_private.fact_origins
            WHERE entity_type='DELIVERY' AND entity_id=v_id;
        IF FOUND THEN
            IF stored_origin IS DISTINCT FROM v_origin THEN
                RAISE EXCEPTION 'sync conflict: ORIGIN_IMMUTABLE' USING ERRCODE='23505';
            END IF;
        ELSE
            -- A row from a legacy writer: the server saw it at created_at, so the device cannot have
            -- created it later (beyond clock margin). Adopted once, then immutable.
            IF v_origin_key>extract(epoch FROM existing.created_at)*1000000000+margin THEN
                RAISE EXCEPTION 'sync review: ORIGIN_INCOHERENT' USING ERRCODE='22023';
            END IF;
            adopt := true;
        END IF;
        unchanged := existing.delivery_date=v_date AND existing.delivery_time IS NOT DISTINCT FROM v_time
            AND NOT EXISTS (
                SELECT parcel_id, id, parcel_name_at_delivery, campaign_parcel_id FROM public.delivery_parcels
                WHERE delivery_id=v_id
                EXCEPT
                SELECT (value->>'parcelId')::uuid, (value->>'id')::uuid, value->>'parcelNameAtDelivery',
                    (value->>'campaignParcelId')::uuid FROM jsonb_array_elements(p_delivery->'parcels'))
            AND NOT EXISTS (
                SELECT (value->>'parcelId')::uuid, (value->>'id')::uuid, value->>'parcelNameAtDelivery',
                    (value->>'campaignParcelId')::uuid FROM jsonb_array_elements(p_delivery->'parcels')
                EXCEPT
                SELECT parcel_id, id, parcel_name_at_delivery, campaign_parcel_id FROM public.delivery_parcels
                WHERE delivery_id=v_id);
        archived_involved := EXISTS (SELECT 1 FROM public.parcels WHERE id=ANY(all_ids)
            AND NOT (status='ACTIVE' AND deleted_at IS NULL));
        -- D4: with an archived parcel involved, date, time, parcel set and snapshots are frozen.
        -- Weights, ticket, destination and notes stay correctable.
        IF archived_involved AND NOT unchanged THEN
            RAISE EXCEPTION 'sync conflict: HISTORICAL_BINDING_FROZEN' USING ERRCODE='23505';
        END IF;
    END IF;

    IF is_update AND unchanged THEN
        -- The binding was already accepted by the server; only parcel coherence is re-checked.
        PERFORM magina_g2_private.historical_parcel_archive_key(p, ws) FROM unnest(parcel_ids) p;
    ELSE
        IF v_date>(now() AT TIME ZONE tz)::date THEN
            RAISE EXCEPTION 'sync review: FUTURE_DATE' USING ERRCODE='22023';
        END IF;
        IF camp.start_date>v_date OR (camp.end_date IS NOT NULL AND camp.end_date<v_date) THEN
            RAISE EXCEPTION 'sync review: CAMPAIGN_PERIOD' USING ERRCODE='22023';
        END IF;
        IF v_time IS NOT NULL THEN
            instant := magina_g2_private.resolve_civil_time(v_date, v_time, tz);
            IF instant>now()+interval '300 seconds' THEN
                RAISE EXCEPTION 'sync review: FUTURE_DATE' USING ERRCODE='22023';
            END IF;
            timed := true;
            fact_from := extract(epoch FROM instant)*1000000000;
            fact_to := fact_from;
        ELSE
            timed := false;
            fact_from := magina_g2_private.local_day_key(v_date, tz);
            fact_to := magina_g2_private.local_day_key(v_date+1, tz);
        END IF;
        PERFORM magina_g2_private.check_historical_parcel(ws, v_farm, p, fact_from, fact_to, timed, v_origin_key, true)
            FROM unnest(parcel_ids) p;
    END IF;

    -- Campaign snapshot binding, as in v2.
    IF camp.snapshot_format=1 AND EXISTS (
        SELECT 1 FROM jsonb_array_elements(p_delivery->'parcels') s(value)
        WHERE value->>'campaignParcelId' IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM public.campaign_parcels cp WHERE cp.id=(value->>'campaignParcelId')::uuid
              AND cp.campaign_id=v_campaign AND cp.parcel_id=(value->>'parcelId')::uuid AND cp.workspace_id=ws)) THEN
        RAISE EXCEPTION 'sync invalid: CAMPAIGN_PARCEL_SNAPSHOT' USING ERRCODE='22023';
    END IF;

    IF is_update THEN
        -- trg_deliveries_immutability increments version and keeps farm, campaign and author.
        UPDATE public.deliveries SET delivery_date=v_date, destination_name=p_delivery->>'destinationName',
            net_grams=v_net, gross_grams=v_gross, tare_grams=v_tare, delivery_number=p_delivery->>'deliveryNumber',
            ticket_number=p_delivery->>'ticketNumber', source=v_source, notes=p_delivery->>'notes',
            harvest_id=v_harvest, delivery_time=v_time, harvest_origin=p_delivery->>'harvestOrigin',
            destination_organization_id=v_dest_org, snapshot_format=2
        WHERE id=v_id AND workspace_id=ws AND version=existing.version;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync conflict: CONCURRENT_UPDATE' USING ERRCODE='23505';
        END IF;
    ELSE
        INSERT INTO public.deliveries(id, workspace_id, farm_id, campaign_id, delivery_date, destination_name,
            net_grams, gross_grams, tare_grams, delivery_number, ticket_number, source, notes, harvest_id,
            delivery_time, harvest_origin, recorded_by_user_id, created_at, updated_at, version,
            destination_organization_id, snapshot_format)
        VALUES (v_id, ws, v_farm, v_campaign, v_date, p_delivery->>'destinationName', v_net, v_gross, v_tare,
            p_delivery->>'deliveryNumber', p_delivery->>'ticketNumber', v_source, p_delivery->>'notes', v_harvest,
            v_time, p_delivery->>'harvestOrigin', actor, now(), now(), 1, v_dest_org, 2);
    END IF;
    DELETE FROM public.delivery_parcels WHERE delivery_id=v_id;
    INSERT INTO public.delivery_parcels(id, workspace_id, delivery_id, parcel_id, campaign_parcel_id,
        parcel_name_at_delivery, weight_grams, allocation_mode)
    SELECT (value->>'id')::uuid, ws, v_id, (value->>'parcelId')::uuid, (value->>'campaignParcelId')::uuid,
        value->>'parcelNameAtDelivery', (value->>'weightGrams')::bigint, value->>'allocationMode'
    FROM jsonb_array_elements(p_delivery->'parcels');
    IF NOT is_update OR adopt THEN
        INSERT INTO magina_g2_private.fact_origins(entity_type, entity_id, workspace_id, origin_created_at_exact,
            adopted, recorded_by)
        VALUES ('DELIVERY', v_id, ws, v_origin, adopt, actor);
    END IF;

    SELECT version INTO result_version FROM public.deliveries WHERE id=v_id;
    INSERT INTO magina_g2_private.sync_operations(account_id, operation_id, entity_type, entity_id, payload_hash,
        status, result_version, payload_version)
    VALUES (actor, p_operation_id, 'DELIVERY', v_id, payload_hash, 'APPLIED', result_version, 3) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',v_id,'status',saved.status,
        'remote_version',saved.result_version,'payload_version',3,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.sync_delivery_historical(UUID,JSONB) FROM PUBLIC, anon, authenticated;

-- 9. Expense v2. Farm mandatory (Q5); parcelId may be null and then no parcel rule applies.
CREATE FUNCTION magina_g2_private.sync_expense_historical(p_operation_id UUID, p_expense JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    margin CONSTANT NUMERIC := 300000000000;
    actor UUID; payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE;
    v_id UUID; v_farm UUID; v_campaign UUID; v_parcel UUID; v_date DATE; v_amount BIGINT; v_status TEXT;
    v_origin TEXT; v_origin_key NUMERIC; v_base BIGINT; all_ids UUID[]; locked INTEGER;
    ws UUID; tz TEXT; camp public.campaigns%ROWTYPE; existing public.expenses%ROWTYPE; is_update BOOLEAN;
    stored_origin TEXT; adopt BOOLEAN := false; unchanged BOOLEAN := false; archived_involved BOOLEAN;
    result_version BIGINT;
BEGIN
    actor := magina_g2_private.historical_actor();
    IF p_operation_id IS NULL OR p_expense IS NULL OR jsonb_typeof(p_expense) IS DISTINCT FROM 'object'
       OR octet_length(p_expense::text)>1048576 THEN
        RAISE EXCEPTION 'sync invalid: PAYLOAD' USING ERRCODE='22023';
    END IF;
    IF (SELECT count(*) FROM jsonb_object_keys(p_expense))<>14
       OR NOT p_expense ?& ARRAY['id','farmId','campaignId','parcelId','expenseDate','concept','category','amountMinor',
           'currency','provider','notes','status','baseVersion','originCreatedAt']
       OR EXISTS (SELECT 1 FROM jsonb_each(p_expense) f(key,value)
           WHERE key IN ('id','farmId','expenseDate','concept','category','currency','status','originCreatedAt')
             AND jsonb_typeof(value) IS DISTINCT FROM 'string')
       OR EXISTS (SELECT 1 FROM jsonb_each(p_expense) f(key,value)
           WHERE key IN ('campaignId','parcelId','provider','notes') AND jsonb_typeof(value) NOT IN ('string','null'))
       OR jsonb_typeof(p_expense->'amountMinor') IS DISTINCT FROM 'number' OR (p_expense->>'amountMinor') !~ '^[0-9]{1,18}$'
       OR (jsonb_typeof(p_expense->'baseVersion')<>'null'
           AND (jsonb_typeof(p_expense->'baseVersion')<>'number' OR (p_expense->>'baseVersion') !~ '^[0-9]{1,18}$'))
       OR p_expense->>'expenseDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
       OR length(btrim(p_expense->>'concept')) NOT BETWEEN 1 AND 512
       OR length(btrim(p_expense->>'category')) NOT BETWEEN 1 AND 100
       OR p_expense->>'currency' !~ '^[A-Z]{3}$'
       OR length(p_expense->>'provider')>512 OR length(p_expense->>'notes')>10000
       OR p_expense->>'status' NOT IN ('DRAFT','POSTED') THEN
        RAISE EXCEPTION 'sync invalid: SHAPE' USING ERRCODE='22023';
    END IF;
    BEGIN
        v_id := (p_expense->>'id')::uuid; v_farm := (p_expense->>'farmId')::uuid;
        v_campaign := (p_expense->>'campaignId')::uuid; v_parcel := (p_expense->>'parcelId')::uuid;
        v_date := (p_expense->>'expenseDate')::date; v_amount := (p_expense->>'amountMinor')::bigint;
        v_base := (p_expense->>'baseVersion')::bigint; v_status := p_expense->>'status';
    EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR invalid_datetime_format
        OR datetime_field_overflow THEN
        RAISE EXCEPTION 'sync invalid: SHAPE' USING ERRCODE='22023';
    END;
    -- Same rule as the Android ledger: never negative; a POSTED expense is positive.
    IF v_amount<0 OR (v_status='POSTED' AND v_amount=0) OR (v_base IS NOT NULL AND v_base<1) THEN
        RAISE EXCEPTION 'sync invalid: AMOUNT' USING ERRCODE='22023';
    END IF;
    v_origin := p_expense->>'originCreatedAt';
    v_origin_key := magina_g2_private.origin_key(v_origin);

    payload_hash := encode(sha256(convert_to(p_expense::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'EXPENSE' OR saved.entity_id<>v_id OR saved.payload_hash<>payload_hash
           OR saved.payload_version<>2 THEN
            RAISE EXCEPTION 'sync conflict: RECEIPT_CONFLICT' USING ERRCODE='23505';
        END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',saved.entity_id,
            'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,
            'idempotent',true);
    END IF;

    SELECT l.ws, l.tz INTO ws, tz FROM magina_g2_private.lock_historical_scope(actor, v_farm) l;
    IF v_campaign IS NOT NULL THEN
        SELECT * INTO camp FROM public.campaigns
            WHERE id=v_campaign AND farm_id=v_farm AND workspace_id=ws AND deleted_at IS NULL FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync invalid: CAMPAIGN_SCOPE' USING ERRCODE='22023';
        END IF;
    END IF;
    all_ids := ARRAY(SELECT DISTINCT x FROM unnest(ARRAY[v_parcel,
        (SELECT parcel_id FROM public.expenses WHERE id=v_id)]) x WHERE x IS NOT NULL);
    SELECT count(*) INTO locked FROM (SELECT id FROM public.parcels WHERE id=ANY(all_ids) AND workspace_id=ws
        ORDER BY id FOR SHARE) l;
    IF locked<>cardinality(all_ids) THEN
        RAISE EXCEPTION 'sync denied: PARCEL_SCOPE' USING ERRCODE='42501';
    END IF;
    PERFORM 1 FROM public.farm_parcel_memberships WHERE parcel_id=ANY(all_ids) AND workspace_id=ws
        ORDER BY id FOR SHARE;
    SELECT * INTO existing FROM public.expenses WHERE id=v_id FOR UPDATE;
    is_update := FOUND;

    IF v_origin_key>extract(epoch FROM now())*1000000000+margin THEN
        RAISE EXCEPTION 'sync review: ORIGIN_IN_FUTURE' USING ERRCODE='22023';
    END IF;

    IF is_update THEN
        IF existing.workspace_id<>ws THEN
            RAISE EXCEPTION 'sync denied: SCOPE' USING ERRCODE='42501';
        END IF;
        IF existing.deleted_at IS NOT NULL THEN
            RAISE EXCEPTION 'sync conflict: DELETED' USING ERRCODE='23505';
        END IF;
        -- Q5: v2 never assigns a farm to a farm-less legacy row; that stays a legacy decision.
        IF existing.farm_id IS NULL OR existing.farm_id<>v_farm THEN
            RAISE EXCEPTION 'sync conflict: BINDING_IMMUTABLE' USING ERRCODE='23505';
        END IF;
        IF v_base IS NULL THEN
            RAISE EXCEPTION 'sync conflict: BASE_VERSION_REQUIRED' USING ERRCODE='23505';
        END IF;
        IF existing.version<>v_base THEN
            RAISE EXCEPTION 'sync conflict: STALE_VERSION' USING ERRCODE='23505', DETAIL='remote_version='||existing.version;
        END IF;
        IF existing.parcel_id IS NOT NULL AND NOT existing.parcel_id=ANY(all_ids) THEN
            RAISE EXCEPTION 'sync conflict: CONCURRENT_UPDATE' USING ERRCODE='23505';
        END IF;
        SELECT origin_created_at_exact INTO stored_origin FROM magina_g2_private.fact_origins
            WHERE entity_type='EXPENSE' AND entity_id=v_id;
        IF FOUND THEN
            IF stored_origin IS DISTINCT FROM v_origin THEN
                RAISE EXCEPTION 'sync conflict: ORIGIN_IMMUTABLE' USING ERRCODE='23505';
            END IF;
        ELSE
            IF v_origin_key>extract(epoch FROM existing.created_at)*1000000000+margin THEN
                RAISE EXCEPTION 'sync review: ORIGIN_INCOHERENT' USING ERRCODE='22023';
            END IF;
            adopt := true;
        END IF;
        unchanged := existing.expense_date=v_date AND existing.campaign_id IS NOT DISTINCT FROM v_campaign
            AND existing.parcel_id IS NOT DISTINCT FROM v_parcel;
        archived_involved := EXISTS (SELECT 1 FROM public.parcels WHERE id=ANY(all_ids)
            AND NOT (status='ACTIVE' AND deleted_at IS NULL));
        -- D4: with an archived parcel involved, farm, campaign, parcel and date are frozen.
        -- Amount, concept, category, currency, provider, notes and status stay correctable.
        IF archived_involved AND NOT unchanged THEN
            RAISE EXCEPTION 'sync conflict: HISTORICAL_BINDING_FROZEN' USING ERRCODE='23505';
        END IF;
    END IF;

    IF is_update AND unchanged THEN
        IF v_parcel IS NOT NULL THEN
            PERFORM magina_g2_private.historical_parcel_archive_key(v_parcel, ws);
        END IF;
    ELSE
        IF v_date>(now() AT TIME ZONE tz)::date THEN
            RAISE EXCEPTION 'sync review: FUTURE_DATE' USING ERRCODE='22023';
        END IF;
        IF v_campaign IS NOT NULL AND (camp.start_date>v_date OR (camp.end_date IS NOT NULL AND camp.end_date<v_date)) THEN
            RAISE EXCEPTION 'sync review: CAMPAIGN_PERIOD' USING ERRCODE='22023';
        END IF;
        IF v_parcel IS NOT NULL THEN
            PERFORM magina_g2_private.check_historical_parcel(ws, v_farm, v_parcel,
                magina_g2_private.local_day_key(v_date, tz), magina_g2_private.local_day_key(v_date+1, tz),
                false, v_origin_key, true);
        END IF;
    END IF;

    IF is_update THEN
        UPDATE public.expenses SET campaign_id=v_campaign, parcel_id=v_parcel, expense_date=v_date,
            concept=p_expense->>'concept', category=p_expense->>'category', amount_minor=v_amount,
            currency=p_expense->>'currency', provider=p_expense->>'provider', notes=p_expense->>'notes',
            status=v_status, version=version+1, updated_at=now()
        WHERE id=v_id AND workspace_id=ws AND version=existing.version;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync conflict: CONCURRENT_UPDATE' USING ERRCODE='23505';
        END IF;
    ELSE
        INSERT INTO public.expenses(id, workspace_id, farm_id, campaign_id, parcel_id, expense_date, concept, category,
            amount_minor, currency, provider, notes, status, origin, version, created_at, updated_at)
        VALUES (v_id, ws, v_farm, v_campaign, v_parcel, v_date, p_expense->>'concept', p_expense->>'category',
            v_amount, p_expense->>'currency', p_expense->>'provider', p_expense->>'notes', v_status, 'MANUAL', 1,
            now(), now());
    END IF;
    IF NOT is_update OR adopt THEN
        INSERT INTO magina_g2_private.fact_origins(entity_type, entity_id, workspace_id, origin_created_at_exact,
            adopted, recorded_by)
        VALUES ('EXPENSE', v_id, ws, v_origin, adopt, actor);
    END IF;

    SELECT version INTO result_version FROM public.expenses WHERE id=v_id;
    INSERT INTO magina_g2_private.sync_operations(account_id, operation_id, entity_type, entity_id, payload_hash,
        status, result_version, payload_version)
    VALUES (actor, p_operation_id, 'EXPENSE', v_id, payload_hash, 'APPLIED', result_version, 2) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',v_id,'status',saved.status,
        'remote_version',saved.result_version,'payload_version',2,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.sync_expense_historical(UUID,JSONB) FROM PUBLIC, anon, authenticated;

-- 10. Thin public endpoints. The legacy endpoints keep their own contracts.
CREATE FUNCTION public.sync_own_delivery_v3(p_operation_id UUID, p_delivery JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.sync_delivery_historical(p_operation_id, p_delivery);
$$;
CREATE FUNCTION public.sync_own_expense_v2(p_operation_id UUID, p_expense JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.sync_expense_historical(p_operation_id, p_expense);
$$;
REVOKE ALL ON FUNCTION public.sync_own_delivery_v3(UUID,JSONB) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.sync_own_expense_v2(UUID,JSONB) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.sync_own_delivery_v3(UUID,JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.sync_own_expense_v2(UUID,JSONB) TO authenticated;

-- 11. Inventory, inside the same transaction: fail the whole load if anything is wider than intended.
DO $$
DECLARE role_name TEXT; fn TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon','authenticated'] LOOP
        FOREACH fn IN ARRAY ARRAY['magina_g2_private.sync_delivery_historical(uuid,jsonb)',
            'magina_g2_private.sync_expense_historical(uuid,jsonb)','magina_g2_private.historical_actor()',
            'magina_g2_private.resolve_civil_time(date,text,text)','magina_g2_private.local_day_key(date,text)',
            'magina_g2_private.historical_parcel_archive_key(uuid,uuid)',
            'magina_g2_private.check_historical_parcel(uuid,uuid,uuid,numeric,numeric,boolean,numeric,boolean)',
            'magina_g2_private.lock_historical_scope(uuid,uuid)','magina_g2_private.origin_key(text)'] LOOP
            IF has_function_privilege(role_name, fn, 'EXECUTE') THEN
                RAISE EXCEPTION 'historical writer helper % is executable by %', fn, role_name;
            END IF;
        END LOOP;
        IF has_table_privilege(role_name, 'magina_g2_private.fact_origins', 'SELECT')
           OR has_table_privilege(role_name, 'magina_g2_private.fact_origins', 'INSERT')
           OR has_table_privilege(role_name, 'magina_g2_private.fact_origins', 'UPDATE')
           OR has_table_privilege(role_name, 'magina_g2_private.fact_origins', 'DELETE') THEN
            RAISE EXCEPTION 'fact_origins is reachable by %', role_name;
        END IF;
    END LOOP;
    IF has_function_privilege('anon', 'public.sync_own_delivery_v3(uuid,jsonb)', 'EXECUTE')
       OR has_function_privilege('anon', 'public.sync_own_expense_v2(uuid,jsonb)', 'EXECUTE')
       OR NOT has_function_privilege('authenticated', 'public.sync_own_delivery_v3(uuid,jsonb)', 'EXECUTE')
       OR NOT has_function_privilege('authenticated', 'public.sync_own_expense_v2(uuid,jsonb)', 'EXECUTE') THEN
        RAISE EXCEPTION 'historical writer endpoint privileges are not exactly authenticated-only';
    END IF;
END $$;
