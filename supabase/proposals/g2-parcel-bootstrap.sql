-- G2 parcel + membership creation. Candidate only; no persistent deployment.
-- Load after g1c-reservations.sql and g2-sync-own-data.sql in the disposable runner.

-- PostgreSQL stores microseconds; retain the exact Android Instant alongside it.
CREATE FUNCTION magina_g2_private.parse_instant(value TEXT) RETURNS TIMESTAMPTZ
LANGUAGE plpgsql STABLE STRICT SET search_path='' AS $$
BEGIN
    IF value !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]([.][0-9]{1,9})?Z$' THEN
        RAISE EXCEPTION 'parcel invalid instant' USING ERRCODE='22023';
    END IF;
    -- Floor extra fractional digits, avoiding rounding across a day boundary.
    RETURN (CASE WHEN length(value)>27 THEN left(value,26)||'Z' ELSE value END)::timestamptz;
EXCEPTION WHEN datetime_field_overflow OR invalid_datetime_format THEN
    RAISE EXCEPTION 'parcel invalid instant' USING ERRCODE='22023';
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.parse_instant(TEXT) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION magina_g2_private.instant_key(value TEXT) RETURNS NUMERIC
LANGUAGE sql STABLE STRICT SET search_path='' AS $$
    SELECT extract(epoch FROM magina_g2_private.parse_instant(value))*1000000000+
        CASE WHEN length(value)>27 THEN rpad(substring(value FROM 27 FOR length(value)-27),3,'0')::integer ELSE 0 END;
$$;
REVOKE ALL ON FUNCTION magina_g2_private.instant_key(TEXT) FROM PUBLIC,anon,authenticated;

ALTER TABLE public.parcels ADD COLUMN IF NOT EXISTS snapshot_format INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.parcels ADD COLUMN IF NOT EXISTS source_imported_at_exact TEXT;
ALTER TABLE public.farm_parcel_memberships ADD COLUMN IF NOT EXISTS valid_from_exact TEXT;
ALTER TABLE public.farm_parcel_memberships ADD COLUMN IF NOT EXISTS valid_until_exact TEXT;
ALTER TABLE public.farm_parcel_memberships ADD COLUMN IF NOT EXISTS deleted_at_exact TEXT;

-- External typed-time edits must not leave a stale exact value that could rewind a restore.
CREATE FUNCTION magina_g2_private.guard_exact_instants() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE data JSONB:=to_jsonb(NEW); previous JSONB; field TEXT; exact_field TEXT;
BEGIN
    IF TG_OP='UPDATE' THEN previous:=to_jsonb(OLD); END IF;
    FOREACH field IN ARRAY CASE WHEN TG_TABLE_NAME='parcels' THEN ARRAY['source_imported_at']
        ELSE ARRAY['valid_from','valid_until','deleted_at'] END LOOP
        exact_field:=field||'_exact';
        IF TG_OP='UPDATE' AND data->field IS DISTINCT FROM previous->field
           AND data->exact_field IS NOT DISTINCT FROM previous->exact_field THEN
            data:=jsonb_set(data,ARRAY[exact_field],'null'::jsonb);
        ELSIF data->>exact_field IS NOT NULL AND
            magina_g2_private.parse_instant(data->>exact_field) IS DISTINCT FROM (data->>field)::timestamptz THEN
            RAISE EXCEPTION 'parcel inconsistent instant' USING ERRCODE='22023';
        END IF;
    END LOOP;
    NEW:=jsonb_populate_record(NEW,data); RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.guard_exact_instants() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER g2_parcel_exact_instants BEFORE INSERT OR UPDATE ON public.parcels
FOR EACH ROW EXECUTE FUNCTION magina_g2_private.guard_exact_instants();
CREATE TRIGGER g2_membership_exact_instants BEFORE INSERT OR UPDATE ON public.farm_parcel_memberships
FOR EACH ROW EXECUTE FUNCTION magina_g2_private.guard_exact_instants();

CREATE FUNCTION magina_g2_private.create_parcel(p_operation_id UUID,p_parcel JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID:=auth.uid(); claims JSONB:=auth.jwt(); parcel UUID; workspace UUID; field TEXT; item JSONB;
    membership UUID; farm UUID; farm_ids UUID[]:='{}'; member_ids UUID[]:='{}'; tree_count INTEGER;
    cadastral_area DOUBLE PRECISION; managed_area DOUBLE PRECISION; imported TIMESTAMPTZ;
    starts TIMESTAMPTZ; ends TIMESTAMPTZ; deleted TIMESTAMPTZ;
    payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE;
BEGIN
    IF actor IS NULL OR claims IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR (claims->>'exp') !~ '^[0-9]+$' THEN
        RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501';
    END IF;
    IF (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501';
    END IF;
    IF p_operation_id IS NULL OR p_parcel IS NULL OR jsonb_typeof(p_parcel)<>'object'
       OR (SELECT count(*) FROM jsonb_object_keys(p_parcel))<>23
       OR NOT p_parcel ?& ARRAY['id','workspaceId','displayName','cadastralReference','cadastralPolygon','cadastralParcel',
           'municipality','province','source','geometryGeoJson','sourceProvider','sourceImportedAt','cadastralAreaM2',
           'managedAreaM2','notes','status','oliveTreeCount','variety','irrigationSystem','irrigationNetwork','irrigationSector',
           'irrigationDays','memberships'] OR octet_length(p_parcel::text)>4194304 THEN
        RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
    END IF;
    FOREACH field IN ARRAY ARRAY['id','workspaceId','displayName','source','status'] LOOP
        IF jsonb_typeof(p_parcel->field) IS DISTINCT FROM 'string' THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    FOREACH field IN ARRAY ARRAY['cadastralReference','cadastralPolygon','cadastralParcel','municipality','province',
        'geometryGeoJson','sourceProvider','sourceImportedAt','notes','variety','irrigationSystem','irrigationNetwork',
        'irrigationSector','irrigationDays'] LOOP
        IF jsonb_typeof(p_parcel->field) NOT IN ('string','null') THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END IF;
        IF field NOT IN ('notes','geometryGeoJson') AND length(p_parcel->>field)>200 THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    FOREACH field IN ARRAY ARRAY['cadastralAreaM2','managedAreaM2','oliveTreeCount'] LOOP
        IF jsonb_typeof(p_parcel->field) NOT IN ('number','null') THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    BEGIN
        parcel:=(p_parcel->>'id')::uuid; workspace:=(p_parcel->>'workspaceId')::uuid;
        tree_count:=(p_parcel->>'oliveTreeCount')::integer;
        cadastral_area:=(p_parcel->>'cadastralAreaM2')::double precision;
        managed_area:=(p_parcel->>'managedAreaM2')::double precision;
        imported:=magina_g2_private.parse_instant(p_parcel->>'sourceImportedAt');
    EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
        RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
    END;
    IF length(p_parcel->>'displayName') NOT BETWEEN 1 AND 200 OR btrim(p_parcel->>'displayName')=''
       OR p_parcel->>'source' NOT IN ('MANUAL','CATASTRO') OR p_parcel->>'status' NOT IN ('ACTIVE','ARCHIVED')
       OR length(p_parcel->>'notes')>10000 OR cadastral_area<0 OR managed_area<0 OR tree_count<0
       OR (p_parcel->>'oliveTreeCount' IS NOT NULL AND p_parcel->>'oliveTreeCount' !~ '^[0-9]+$')
       OR (p_parcel->>'irrigationSystem' IS NOT NULL AND p_parcel->>'irrigationSystem' NOT IN ('DRYLAND','DRIP','SPRINKLER','OTHER'))
       OR (p_parcel->>'source'='CATASTRO' AND (coalesce(btrim(p_parcel->>'cadastralReference'),'')=''
           OR coalesce(btrim(p_parcel->>'geometryGeoJson'),'')=''))
       OR jsonb_typeof(p_parcel->'memberships') IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
    END IF;
    IF jsonb_array_length(p_parcel->'memberships') NOT BETWEEN 1 AND 1000 THEN
        RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
    END IF;
    FOR item IN SELECT value FROM jsonb_array_elements(p_parcel->'memberships') LOOP
        IF jsonb_typeof(item)<>'object' OR (SELECT count(*) FROM jsonb_object_keys(item))<>5
           OR NOT item ?& ARRAY['id','farmId','validFrom','validUntil','deletedAt'] THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END IF;
        FOREACH field IN ARRAY ARRAY['id','farmId','validFrom'] LOOP
            IF jsonb_typeof(item->field) IS DISTINCT FROM 'string' THEN
                RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
            END IF;
        END LOOP;
        IF jsonb_typeof(item->'validUntil') NOT IN ('string','null') OR jsonb_typeof(item->'deletedAt') NOT IN ('string','null') THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END IF;
        BEGIN
            membership:=(item->>'id')::uuid; farm:=(item->>'farmId')::uuid;
            starts:=magina_g2_private.parse_instant(item->>'validFrom');
            ends:=magina_g2_private.parse_instant(item->>'validUntil');
            deleted:=magina_g2_private.parse_instant(item->>'deletedAt');
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END;
        IF membership=ANY(member_ids) OR magina_g2_private.instant_key(item->>'validUntil')<magina_g2_private.instant_key(item->>'validFrom') THEN
            RAISE EXCEPTION 'parcel invalid' USING ERRCODE='22023';
        END IF;
        member_ids:=array_append(member_ids,membership); farm_ids:=array_append(farm_ids,farm);
    END LOOP;
    IF EXISTS (
        WITH periods AS MATERIALIZED (
            SELECT ordinality AS seq,(value->>'farmId')::uuid AS farm_id,
                magina_g2_private.instant_key(value->>'validFrom') AS lo,
                magina_g2_private.instant_key(value->>'validUntil') AS hi,value->>'deletedAt' AS removed
            FROM jsonb_array_elements(p_parcel->'memberships') WITH ORDINALITY
        ) SELECT 1 FROM periods a JOIN periods b ON a.seq<b.seq
          WHERE (a.farm_id=b.farm_id AND a.lo=b.lo) OR
            (a.removed IS NULL AND b.removed IS NULL AND (a.hi IS NULL OR a.hi>a.lo) AND (b.hi IS NULL OR b.hi>b.lo)
             AND (a.hi IS NULL OR b.lo<a.hi) AND (b.hi IS NULL OR a.lo<b.hi))
    ) THEN RAISE EXCEPTION 'parcel invalid memberships' USING ERRCODE='22023'; END IF;
    payload_hash:=encode(sha256(convert_to(p_parcel::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    PERFORM 1 FROM public.workspaces WHERE id=workspace AND owner_user_id=actor AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501'; END IF;
    FOR farm IN SELECT DISTINCT value FROM unnest(farm_ids) AS f(value) ORDER BY value LOOP
        PERFORM 1 FROM public.farms WHERE id=farm AND workspace_id=workspace AND deleted_at IS NULL
            AND (creator_user_id=actor OR owner_id=actor) FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501'; END IF;
    END LOOP;
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'PARCEL' OR saved.entity_id<>parcel OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'parcel conflict' USING ERRCODE='23505';
        END IF;
        PERFORM 1 FROM public.parcels WHERE id=parcel AND workspace_id=workspace AND deleted_at IS NULL AND snapshot_format=1 FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501'; END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',parcel,
            'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',true);
    END IF;
    INSERT INTO public.parcels(id,workspace_id,display_name,cadastral_reference,cadastral_polygon,cadastral_parcel,
        municipality,province,source,geometry_geo_json,source_provider,source_imported_at,source_imported_at_exact,
        cadastral_area_m2,managed_area_m2,notes,status,olive_tree_count,variety,irrigation_system,irrigation_network,
        irrigation_sector,irrigation_days,snapshot_format)
    VALUES(parcel,workspace,p_parcel->>'displayName',p_parcel->>'cadastralReference',p_parcel->>'cadastralPolygon',
        p_parcel->>'cadastralParcel',p_parcel->>'municipality',p_parcel->>'province',p_parcel->>'source',p_parcel->>'geometryGeoJson',
        p_parcel->>'sourceProvider',imported,p_parcel->>'sourceImportedAt',cadastral_area,managed_area,p_parcel->>'notes',
        p_parcel->>'status',tree_count,p_parcel->>'variety',p_parcel->>'irrigationSystem',p_parcel->>'irrigationNetwork',
        p_parcel->>'irrigationSector',p_parcel->>'irrigationDays',1) ON CONFLICT(id) DO NOTHING;
    IF NOT FOUND THEN RAISE EXCEPTION 'parcel conflict' USING ERRCODE='23505'; END IF;
    FOR item IN SELECT value FROM jsonb_array_elements(p_parcel->'memberships') LOOP
        INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_until,deleted_at,
            valid_from_exact,valid_until_exact,deleted_at_exact)
        VALUES((item->>'id')::uuid,workspace,(item->>'farmId')::uuid,parcel,
            magina_g2_private.parse_instant(item->>'validFrom'),magina_g2_private.parse_instant(item->>'validUntil'),
            magina_g2_private.parse_instant(item->>'deletedAt'),item->>'validFrom',item->>'validUntil',item->>'deletedAt');
    END LOOP;
    INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,payload_hash,result_version)
    VALUES(actor,p_operation_id,'PARCEL',parcel,payload_hash,1) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',parcel,
        'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.create_parcel(UUID,JSONB) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.create_own_parcel(p_operation_id UUID,p_parcel JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.create_parcel(p_operation_id,p_parcel);
$$;
REVOKE ALL ON FUNCTION public.create_own_parcel(UUID,JSONB) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.create_own_parcel(UUID,JSONB) TO authenticated;

CREATE FUNCTION public.get_own_parcel(p_parcel_id UUID) RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID:=auth.uid(); claims JSONB:=auth.jwt(); result JSONB;
BEGIN
    IF actor IS NULL OR claims IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR (claims->>'exp') !~ '^[0-9]+$' THEN
        RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501';
    END IF;
    IF (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501'; END IF;
    SELECT jsonb_build_object('payload_version',1,'parcel',to_jsonb(p),'memberships',coalesce((
        SELECT jsonb_agg(to_jsonb(m) ORDER BY coalesce(magina_g2_private.instant_key(m.valid_from_exact),
            extract(epoch FROM m.valid_from)*1000000000),m.id) FROM public.farm_parcel_memberships m WHERE m.parcel_id=p.id
    ),'[]'::jsonb)) INTO result FROM public.parcels p JOIN public.workspaces w ON w.id=p.workspace_id
    WHERE p.id=p_parcel_id AND p.snapshot_format=1 AND p.deleted_at IS NULL AND w.deleted_at IS NULL AND w.owner_user_id=actor
      AND EXISTS(SELECT 1 FROM magina_g2_private.sync_operations WHERE account_id=actor AND entity_type='PARCEL' AND entity_id=p.id)
      AND NOT EXISTS(SELECT 1 FROM public.farm_parcel_memberships m LEFT JOIN public.farms f ON f.id=m.farm_id
          WHERE m.parcel_id=p.id AND (f.id IS NULL OR f.workspace_id<>p.workspace_id OR f.deleted_at IS NOT NULL
              OR NOT(coalesce(f.creator_user_id=actor,false) OR coalesce(f.owner_id=actor,false))));
    IF result IS NULL THEN RAISE EXCEPTION 'parcel denied' USING ERRCODE='42501'; END IF;
    RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.get_own_parcel(UUID) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_parcel(UUID) TO authenticated;
