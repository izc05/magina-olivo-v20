-- G2.6 PARCEL UPDATE / ARCHIVE / RESTORE (contract #803). Proposal only: disposable Supabase.
-- Load after g2-sync-own-data.sql, g2-parcel-bootstrap.sql and g26-fpm-manage-hardening.sql.
-- NEVER apply to DEV or production without a separate deployment decision and backup/restore plan.
--
-- Three thin owner-only RPCs over one private transactional core. The direct INSERT/UPDATE of
-- parcels is revoked in this same file, so no client path can skip the CAS.

-- Exact Android instant of an archive; PostgreSQL keeps only microseconds in deleted_at.
ALTER TABLE public.parcels ADD COLUMN IF NOT EXISTS deleted_at_exact TEXT;

-- Nanosecond ordering key: the exact Android value when present, the timestamptz otherwise.
CREATE FUNCTION magina_g2_private.moment_key(value TIMESTAMPTZ, exact TEXT) RETURNS NUMERIC
LANGUAGE sql STABLE SET search_path='' AS $$
    SELECT CASE WHEN exact IS NULL THEN extract(epoch FROM value)*1000000000 ELSE magina_g2_private.instant_key(exact) END;
$$;
REVOKE ALL ON FUNCTION magina_g2_private.moment_key(TIMESTAMPTZ,TEXT) FROM PUBLIC,anon,authenticated;

-- The parcel's own fields: create_own_parcel's payload without id, workspaceId, status or memberships.
-- Status changes only through archive/restore.
CREATE FUNCTION magina_g2_private.validate_parcel_fields(p JSONB) RETURNS VOID
LANGUAGE plpgsql STABLE SET search_path='' AS $$
DECLARE field TEXT;
BEGIN
    IF (SELECT count(*) FROM jsonb_object_keys(p))<>19
       OR NOT p ?& ARRAY['displayName','cadastralReference','cadastralPolygon','cadastralParcel','municipality','province',
           'source','geometryGeoJson','sourceProvider','sourceImportedAt','cadastralAreaM2','managedAreaM2','notes',
           'oliveTreeCount','variety','irrigationSystem','irrigationNetwork','irrigationSector','irrigationDays'] THEN
        RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
    END IF;
    IF jsonb_typeof(p->'displayName') IS DISTINCT FROM 'string' OR jsonb_typeof(p->'source') IS DISTINCT FROM 'string' THEN
        RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
    END IF;
    FOREACH field IN ARRAY ARRAY['cadastralReference','cadastralPolygon','cadastralParcel','municipality','province',
        'geometryGeoJson','sourceProvider','sourceImportedAt','notes','variety','irrigationSystem','irrigationNetwork',
        'irrigationSector','irrigationDays'] LOOP
        IF jsonb_typeof(p->field) NOT IN ('string','null') THEN
            RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
        END IF;
        IF field NOT IN ('notes','geometryGeoJson') AND length(p->>field)>200 THEN
            RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    FOREACH field IN ARRAY ARRAY['cadastralAreaM2','managedAreaM2','oliveTreeCount'] LOOP
        IF jsonb_typeof(p->field) NOT IN ('number','null') THEN
            RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    IF length(p->>'displayName') NOT BETWEEN 1 AND 200 OR btrim(p->>'displayName')=''
       OR p->>'source' NOT IN ('MANUAL','CATASTRO') OR length(p->>'notes')>10000
       OR (p->>'cadastralAreaM2')::double precision<0 OR (p->>'managedAreaM2')::double precision<0
       OR (p->>'oliveTreeCount' IS NOT NULL AND p->>'oliveTreeCount' !~ '^[0-9]+$')
       OR (p->>'irrigationSystem' IS NOT NULL AND p->>'irrigationSystem' NOT IN ('DRYLAND','DRIP','SPRINKLER','OTHER'))
       OR (p->>'source'='CATASTRO' AND (coalesce(btrim(p->>'cadastralReference'),'')=''
           OR coalesce(btrim(p->>'geometryGeoJson'),'')='')) THEN
        RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
    END IF;
    PERFORM (p->>'oliveTreeCount')::integer, magina_g2_private.parse_instant(p->>'sourceImportedAt');
EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
    RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.validate_parcel_fields(JSONB) FROM PUBLIC,anon,authenticated;

-- An owned, live farm of this workspace, locked against archive while the mutation commits.
CREATE FUNCTION magina_g2_private.lock_owned_farm(farm UUID, workspace UUID, actor UUID) RETURNS VOID
LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
    PERFORM 1 FROM public.farms WHERE id=farm AND workspace_id=workspace AND deleted_at IS NULL
        AND (creator_user_id=actor OR owner_id=actor) FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'parcel mutation denied' USING ERRCODE='42501'; END IF;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.lock_owned_farm(UUID,UUID,UUID) FROM PUBLIC,anon,authenticated;

-- No other active parcel of the workspace may hold the same cadastral reference (no server index exists).
CREATE FUNCTION magina_g2_private.claim_cadastral_reference(reference TEXT, workspace UUID, parcel UUID) RETURNS VOID
LANGUAGE plpgsql SET search_path='' AS $$
DECLARE normalized TEXT:=upper(btrim(reference));
BEGIN
    IF coalesce(normalized,'')='' THEN RETURN; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('parcel-cadastral:'||workspace::text||':'||normalized,0));
    IF EXISTS (SELECT 1 FROM public.parcels WHERE workspace_id=workspace AND id<>parcel AND deleted_at IS NULL
               AND status='ACTIVE' AND upper(btrim(cadastral_reference))=normalized) THEN
        RAISE EXCEPTION 'parcel conflict: DUPLICATE_CADASTRAL_REFERENCE' USING ERRCODE='23505';
    END IF;
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.claim_cadastral_reference(TEXT,UUID,UUID) FROM PUBLIC,anon,authenticated;

CREATE FUNCTION magina_g2_private.mutate_parcel(p_kind TEXT,p_operation_id UUID,p_parcel_id UUID,
    p_expected_version BIGINT,p_mutation JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID:=auth.uid(); claims JSONB:=auth.jwt(); entity TEXT; workspace UUID; payload_hash TEXT;
    cur public.parcels%ROWTYPE; membership public.farm_parcel_memberships%ROWTYPE;
    saved magina_g2_private.sync_operations%ROWTYPE; next_version BIGINT; touched JSONB:='[]'::jsonb;
    at_text TEXT; at_key NUMERIC; open_id UUID; target UUID; current_count INTEGER; field TEXT;
BEGIN
    IF actor IS NULL OR claims IS NULL
       OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR claims->>'role' IS DISTINCT FROM 'authenticated' OR claims->>'sub' IS DISTINCT FROM actor::text
       OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR (claims->>'exp') !~ '^[0-9]{1,12}$'
       OR (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'parcel mutation denied' USING ERRCODE='42501';
    END IF;
    entity:=CASE p_kind WHEN 'UPDATE' THEN 'PARCEL_UPDATE' WHEN 'ARCHIVE' THEN 'PARCEL_ARCHIVE'
        WHEN 'RESTORE' THEN 'PARCEL_RESTORE' END;
    IF entity IS NULL OR p_operation_id IS NULL OR p_parcel_id IS NULL OR p_expected_version IS NULL
       OR p_expected_version<1 OR p_mutation IS NULL OR jsonb_typeof(p_mutation)<>'object'
       OR octet_length(p_mutation::text)>4194304 THEN
        RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
    END IF;

    -- Closed shape per operation, before any lock.
    IF p_kind='UPDATE' THEN
        PERFORM magina_g2_private.validate_parcel_fields(p_mutation);
    ELSIF p_kind='ARCHIVE' THEN
        IF (SELECT count(*) FROM jsonb_object_keys(p_mutation))<>2 OR NOT p_mutation ?& ARRAY['archivedAt','closeMembershipId']
           OR jsonb_typeof(p_mutation->'archivedAt') IS DISTINCT FROM 'string'
           OR jsonb_typeof(p_mutation->'closeMembershipId') IS DISTINCT FROM 'string' THEN
            RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
        END IF;
        at_text:=p_mutation->>'archivedAt';
        BEGIN open_id:=(p_mutation->>'closeMembershipId')::uuid;
        EXCEPTION WHEN invalid_text_representation THEN RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023'; END;
    ELSE
        IF (SELECT count(*) FROM jsonb_object_keys(p_mutation))<>2 OR NOT p_mutation ?& ARRAY['restoredAt','openMembership']
           OR jsonb_typeof(p_mutation->'restoredAt') IS DISTINCT FROM 'string'
           OR jsonb_typeof(p_mutation->'openMembership') IS DISTINCT FROM 'object'
           OR (SELECT count(*) FROM jsonb_object_keys(p_mutation->'openMembership'))<>3
           OR NOT p_mutation->'openMembership' ?& ARRAY['id','farmId','validFrom'] THEN
            RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
        END IF;
        FOREACH field IN ARRAY ARRAY['id','farmId','validFrom'] LOOP
            IF jsonb_typeof(p_mutation->'openMembership'->field) IS DISTINCT FROM 'string' THEN
                RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
            END IF;
        END LOOP;
        -- One instant: the parcel comes back exactly when its new membership starts.
        IF p_mutation->>'restoredAt' IS DISTINCT FROM p_mutation->'openMembership'->>'validFrom' THEN
            RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023';
        END IF;
        at_text:=p_mutation->>'restoredAt';
        BEGIN open_id:=(p_mutation->'openMembership'->>'id')::uuid; target:=(p_mutation->'openMembership'->>'farmId')::uuid;
        EXCEPTION WHEN invalid_text_representation THEN RAISE EXCEPTION 'parcel mutation invalid' USING ERRCODE='22023'; END;
    END IF;
    IF at_text IS NOT NULL THEN
        PERFORM magina_g2_private.parse_instant(at_text);
        at_key:=magina_g2_private.instant_key(at_text);
    END IF;

    payload_hash:=encode(sha256(convert_to(jsonb_build_object('kind',p_kind,'parcelId',p_parcel_id,
        'expectedVersion',p_expected_version,'mutation',p_mutation)::text,'UTF8')),'hex');
    -- Same key space as create_parcel: one operation id is serialized whatever RPC carries it.
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));

    -- Fixed lock order: workspace, parcel, then farms. Unknown and foreign parcels look the same.
    SELECT workspace_id INTO workspace FROM public.parcels WHERE id=p_parcel_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'parcel mutation denied' USING ERRCODE='42501'; END IF;
    PERFORM 1 FROM public.workspaces WHERE id=workspace AND owner_user_id=actor AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'parcel mutation denied' USING ERRCODE='42501'; END IF;
    SELECT * INTO cur FROM public.parcels WHERE id=p_parcel_id AND workspace_id=workspace FOR UPDATE;
    IF NOT FOUND OR cur.snapshot_format<>1 THEN RAISE EXCEPTION 'parcel mutation denied' USING ERRCODE='42501'; END IF;

    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>entity OR saved.entity_id<>p_parcel_id OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'parcel conflict: RECEIPT_CONFLICT' USING ERRCODE='23505';
        END IF;
        -- A replay re-checks today's authority over the farm involved: the one the payload names, or for
        -- UPDATE the farm of the current membership (the most recent one if the parcel was archived since).
        IF p_kind='UPDATE' THEN
            SELECT * INTO membership FROM public.farm_parcel_memberships WHERE parcel_id=p_parcel_id AND deleted_at IS NULL
                ORDER BY (valid_until IS NULL) DESC,magina_g2_private.moment_key(valid_from,valid_from_exact) DESC,id LIMIT 1;
            PERFORM magina_g2_private.lock_owned_farm(membership.farm_id,workspace,actor);
        ELSIF p_kind='ARCHIVE' THEN
            SELECT * INTO membership FROM public.farm_parcel_memberships WHERE id=open_id AND parcel_id=p_parcel_id;
            PERFORM magina_g2_private.lock_owned_farm(membership.farm_id,workspace,actor);
            touched:=jsonb_build_array(jsonb_build_object('id',membership.id,'version',membership.version));
        ELSIF p_kind='RESTORE' THEN
            PERFORM magina_g2_private.lock_owned_farm(target,workspace,actor);
            touched:=jsonb_build_array(jsonb_build_object('id',open_id,'version',1));
        END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',p_parcel_id,'kind',p_kind,
            'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,
            'memberships',touched,'idempotent',true);
    END IF;

    IF cur.version<>p_expected_version THEN
        RAISE EXCEPTION 'parcel conflict: PARCEL_VERSION_CONFLICT'
            USING ERRCODE='23505', DETAIL='remote_version='||cur.version;
    END IF;
    next_version:=cur.version+1;

    IF p_kind='UPDATE' THEN
        IF cur.status<>'ACTIVE' OR cur.deleted_at IS NOT NULL THEN
            RAISE EXCEPTION 'parcel conflict: PARCEL_NOT_ACTIVE' USING ERRCODE='23505';
        END IF;
        SELECT * INTO membership FROM public.farm_parcel_memberships
            WHERE parcel_id=p_parcel_id AND deleted_at IS NULL AND valid_until IS NULL;
        IF NOT FOUND THEN RAISE EXCEPTION 'parcel inconsistent' USING ERRCODE='23514'; END IF;
        PERFORM magina_g2_private.lock_owned_farm(membership.farm_id,workspace,actor);
        PERFORM magina_g2_private.claim_cadastral_reference(p_mutation->>'cadastralReference',workspace,p_parcel_id);
        UPDATE public.parcels SET display_name=p_mutation->>'displayName',cadastral_reference=p_mutation->>'cadastralReference',
            cadastral_polygon=p_mutation->>'cadastralPolygon',cadastral_parcel=p_mutation->>'cadastralParcel',
            municipality=p_mutation->>'municipality',province=p_mutation->>'province',source=p_mutation->>'source',
            geometry_geo_json=p_mutation->>'geometryGeoJson',source_provider=p_mutation->>'sourceProvider',
            source_imported_at=magina_g2_private.parse_instant(p_mutation->>'sourceImportedAt'),
            source_imported_at_exact=p_mutation->>'sourceImportedAt',
            cadastral_area_m2=(p_mutation->>'cadastralAreaM2')::double precision,
            managed_area_m2=(p_mutation->>'managedAreaM2')::double precision,notes=p_mutation->>'notes',
            olive_tree_count=(p_mutation->>'oliveTreeCount')::integer,variety=p_mutation->>'variety',
            irrigation_system=p_mutation->>'irrigationSystem',irrigation_network=p_mutation->>'irrigationNetwork',
            irrigation_sector=p_mutation->>'irrigationSector',irrigation_days=p_mutation->>'irrigationDays',
            version=next_version,updated_at=now()
        WHERE id=p_parcel_id;

    ELSIF p_kind='ARCHIVE' THEN
        IF cur.status<>'ACTIVE' OR cur.deleted_at IS NOT NULL THEN
            RAISE EXCEPTION 'parcel conflict: PARCEL_NOT_ACTIVE' USING ERRCODE='23505';
        END IF;
        SELECT * INTO membership FROM public.farm_parcel_memberships
            WHERE id=open_id AND parcel_id=p_parcel_id AND deleted_at IS NULL AND valid_until IS NULL FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'parcel conflict: MEMBERSHIP_NOT_CURRENT' USING ERRCODE='23505'; END IF;
        PERFORM magina_g2_private.lock_owned_farm(membership.farm_id,workspace,actor);
        IF at_key<magina_g2_private.moment_key(membership.valid_from,membership.valid_from_exact) THEN
            RAISE EXCEPTION 'parcel mutation invalid: archive before membership start' USING ERRCODE='22023';
        END IF;
        -- #427 on the server: a running campaign blocks. Historical deliveries, weighings and
        -- expenses never block; their references stay intact because archiving is logical.
        IF EXISTS (SELECT 1 FROM public.campaign_parcels cp JOIN public.campaigns c ON c.id=cp.campaign_id
                   WHERE cp.parcel_id=p_parcel_id AND c.deleted_at IS NULL AND c.status IN ('PREPARATION','ACTIVE','HARVEST')) THEN
            RAISE EXCEPTION 'parcel conflict: ARCHIVE_BLOCKED_CAMPAIGN' USING ERRCODE='23505';
        END IF;
        UPDATE public.farm_parcel_memberships SET valid_until=magina_g2_private.parse_instant(at_text),
            valid_until_exact=at_text,version=version+1,updated_at=now()
        WHERE id=open_id RETURNING * INTO membership;
        UPDATE public.parcels SET status='ARCHIVED',deleted_at=magina_g2_private.parse_instant(at_text),
            deleted_at_exact=at_text,version=next_version,updated_at=now()
        WHERE id=p_parcel_id;
        touched:=jsonb_build_array(jsonb_build_object('id',membership.id,'version',membership.version));

    ELSE
        IF cur.status<>'ARCHIVED' OR EXISTS (SELECT 1 FROM public.farm_parcel_memberships
               WHERE parcel_id=p_parcel_id AND deleted_at IS NULL AND valid_until IS NULL) THEN
            RAISE EXCEPTION 'parcel conflict: PARCEL_NOT_ARCHIVED' USING ERRCODE='23505';
        END IF;
        IF EXISTS (SELECT 1 FROM public.farm_parcel_memberships WHERE id=open_id) THEN
            RAISE EXCEPTION 'parcel conflict: MEMBERSHIP_ID_TAKEN' USING ERRCODE='23505';
        END IF;
        PERFORM magina_g2_private.lock_owned_farm(target,workspace,actor);
        IF at_key<magina_g2_private.moment_key(cur.deleted_at,cur.deleted_at_exact)
           OR EXISTS (SELECT 1 FROM public.farm_parcel_memberships WHERE parcel_id=p_parcel_id AND deleted_at IS NULL
                      AND at_key<magina_g2_private.moment_key(valid_until,valid_until_exact)) THEN
            RAISE EXCEPTION 'parcel mutation invalid: restore before archive' USING ERRCODE='22023';
        END IF;
        IF cur.source='CATASTRO' AND (coalesce(btrim(cur.cadastral_reference),'')=''
           OR coalesce(btrim(cur.geometry_geo_json),'')='') THEN
            RAISE EXCEPTION 'parcel mutation invalid: catastro identity' USING ERRCODE='22023';
        END IF;
        PERFORM magina_g2_private.claim_cadastral_reference(cur.cadastral_reference,workspace,p_parcel_id);
        INSERT INTO public.farm_parcel_memberships(id,workspace_id,farm_id,parcel_id,valid_from,valid_from_exact)
        VALUES(open_id,workspace,target,p_parcel_id,magina_g2_private.parse_instant(at_text),at_text);
        UPDATE public.parcels SET status='ACTIVE',deleted_at=NULL,deleted_at_exact=NULL,version=next_version,updated_at=now()
        WHERE id=p_parcel_id;
        touched:=jsonb_build_array(jsonb_build_object('id',open_id,'version',1));
    END IF;

    -- Final invariant, inside the same transaction: at most one current membership, no overlapping
    -- live periods, and status, deleted_at and the current membership agree.
    SELECT * INTO cur FROM public.parcels WHERE id=p_parcel_id;
    SELECT count(*) INTO current_count FROM public.farm_parcel_memberships
        WHERE parcel_id=p_parcel_id AND deleted_at IS NULL AND valid_until IS NULL;
    IF NOT ((cur.status='ACTIVE' AND cur.deleted_at IS NULL AND current_count=1)
            OR (cur.status='ARCHIVED' AND cur.deleted_at IS NOT NULL AND current_count=0))
       OR EXISTS (
           WITH periods AS MATERIALIZED (
               SELECT id,magina_g2_private.moment_key(valid_from,valid_from_exact) AS lo,
                   magina_g2_private.moment_key(valid_until,valid_until_exact) AS hi
               FROM public.farm_parcel_memberships WHERE parcel_id=p_parcel_id AND deleted_at IS NULL
           ) SELECT 1 FROM periods a JOIN periods b ON a.id<b.id
             WHERE (a.hi IS NULL OR a.hi>a.lo) AND (b.hi IS NULL OR b.hi>b.lo)
               AND (a.hi IS NULL OR b.lo<a.hi) AND (b.hi IS NULL OR a.lo<b.hi)) THEN
        RAISE EXCEPTION 'parcel inconsistent' USING ERRCODE='23514';
    END IF;

    INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,payload_hash,result_version)
    VALUES(actor,p_operation_id,entity,p_parcel_id,payload_hash,next_version) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',p_parcel_id,'kind',p_kind,
        'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,
        'memberships',touched,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.mutate_parcel(TEXT,UUID,UUID,BIGINT,JSONB) FROM PUBLIC,anon,authenticated;

CREATE FUNCTION public.update_own_parcel_v1(p_operation_id UUID,p_parcel_id UUID,p_expected_version BIGINT,p_parcel JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.mutate_parcel('UPDATE',p_operation_id,p_parcel_id,p_expected_version,p_parcel);
$$;
CREATE FUNCTION public.archive_own_parcel_v1(p_operation_id UUID,p_parcel_id UUID,p_expected_version BIGINT,p_archive JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.mutate_parcel('ARCHIVE',p_operation_id,p_parcel_id,p_expected_version,p_archive);
$$;
CREATE FUNCTION public.restore_own_parcel_v1(p_operation_id UUID,p_parcel_id UUID,p_expected_version BIGINT,p_restore JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.mutate_parcel('RESTORE',p_operation_id,p_parcel_id,p_expected_version,p_restore);
$$;
REVOKE ALL ON FUNCTION public.update_own_parcel_v1(UUID,UUID,BIGINT,JSONB) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.archive_own_parcel_v1(UUID,UUID,BIGINT,JSONB) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.restore_own_parcel_v1(UUID,UUID,BIGINT,JSONB) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.update_own_parcel_v1(UUID,UUID,BIGINT,JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.archive_own_parcel_v1(UUID,UUID,BIGINT,JSONB) TO authenticated;
GRANT EXECUTE ON FUNCTION public.restore_own_parcel_v1(UUID,UUID,BIGINT,JSONB) TO authenticated;

-- Atomic with the RPCs: no direct INSERT/UPDATE of parcels for any client role, including what
-- PUBLIC would grant by inheritance. A table-level REVOKE also removes column-level grants.
-- SELECT stays, so parcels_workspace_owner and parcels_delegated_select keep serving reads.
REVOKE INSERT, UPDATE ON public.parcels FROM PUBLIC, anon, authenticated;

DO $$
DECLARE role_name TEXT; privilege TEXT;
BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon','authenticated'] LOOP
        FOREACH privilege IN ARRAY ARRAY['INSERT','UPDATE','DELETE','TRUNCATE'] LOOP
            IF has_table_privilege(role_name,'public.parcels',privilege)
               OR has_table_privilege(role_name,'public.farm_parcel_memberships',privilege) THEN
                RAISE EXCEPTION 'parcel mutation hardening incomplete: % % remains', role_name, privilege;
            END IF;
        END LOOP;
        FOREACH privilege IN ARRAY ARRAY['INSERT','UPDATE'] LOOP
            IF has_any_column_privilege(role_name,'public.parcels',privilege)
               OR has_any_column_privilege(role_name,'public.farm_parcel_memberships',privilege) THEN
                RAISE EXCEPTION 'parcel mutation hardening incomplete: column % for %', privilege, role_name;
            END IF;
        END LOOP;
        IF has_function_privilege(role_name,'magina_g2_private.mutate_parcel(text,uuid,uuid,bigint,jsonb)','EXECUTE') THEN
            RAISE EXCEPTION 'parcel mutation core is executable by %', role_name;
        END IF;
    END LOOP;
    IF NOT has_table_privilege('authenticated','public.parcels','SELECT')
       OR NOT has_table_privilege('authenticated','public.farm_parcel_memberships','SELECT') THEN
        RAISE EXCEPTION 'parcel mutation hardening removed a legitimate read';
    END IF;
    IF has_function_privilege('anon','public.update_own_parcel_v1(uuid,uuid,bigint,jsonb)','EXECUTE')
       OR has_function_privilege('anon','public.archive_own_parcel_v1(uuid,uuid,bigint,jsonb)','EXECUTE')
       OR has_function_privilege('anon','public.restore_own_parcel_v1(uuid,uuid,bigint,jsonb)','EXECUTE') THEN
        RAISE EXCEPTION 'parcel mutation RPC is executable by anon';
    END IF;
END $$;
