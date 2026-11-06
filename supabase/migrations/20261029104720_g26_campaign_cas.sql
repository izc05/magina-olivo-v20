-- Isolated G2.6 proposal. Do not apply to DEV or production without a separate gate.
-- Creation and historical parcel snapshots remain owned by create_own_campaign.
-- All client campaign header updates must use this CAS RPC.
REVOKE UPDATE ON public.campaigns FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.update_own_campaign_v1(
    p_operation_id uuid, p_expected_version bigint, p_campaign jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    actor uuid := auth.uid();
    claims jsonb := auth.jwt();
    v_campaign_id uuid;
    v_workspace_id uuid;
    v_farm_id uuid;
    start_on date;
    end_on date;
    payload_hash text;
    receipt magina_g2_private.sync_operations%ROWTYPE;
    current_campaign public.campaigns%ROWTYPE;
BEGIN
    IF actor IS NULL OR claims IS NULL
       OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text
       OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR coalesce(claims->>'is_anonymous','false') <> 'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
       OR (claims->>'exp') !~ '^[0-9]+$'
       OR (claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE = '42501';
    END IF;
    IF p_operation_id IS NULL OR p_expected_version IS NULL OR p_expected_version < 1
       OR p_expected_version = 9223372036854775807
       OR p_campaign IS NULL OR jsonb_typeof(p_campaign) <> 'object'
       OR (SELECT count(*) FROM jsonb_object_keys(p_campaign)) <> 8
       OR NOT p_campaign ?& ARRAY['id','workspaceId','farmId','name','startDate','endDate','status','notes']
       OR octet_length(p_campaign::text) > 65536
       OR jsonb_typeof(p_campaign->'id') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_campaign->'workspaceId') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_campaign->'farmId') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_campaign->'name') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_campaign->'startDate') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_campaign->'status') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_campaign->'endDate') NOT IN ('string','null')
       OR jsonb_typeof(p_campaign->'notes') NOT IN ('string','null') THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE = '22023';
    END IF;
    BEGIN
        v_campaign_id := (p_campaign->>'id')::uuid;
        v_workspace_id := (p_campaign->>'workspaceId')::uuid;
        v_farm_id := (p_campaign->>'farmId')::uuid;
        IF p_campaign->>'startDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
           OR (p_campaign->>'endDate' IS NOT NULL
               AND p_campaign->>'endDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') THEN
            RAISE invalid_parameter_value;
        END IF;
        start_on := (p_campaign->>'startDate')::date;
        end_on := (p_campaign->>'endDate')::date;
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow
        OR invalid_datetime_format OR invalid_parameter_value THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE = '22023';
    END;
    IF length(p_campaign->>'name') NOT BETWEEN 1 AND 200
       OR btrim(p_campaign->>'name') = ''
       OR p_campaign->>'status' NOT IN ('PREPARATION','ACTIVE','HARVEST','CLOSED')
       OR length(p_campaign->>'notes') > 10000
       OR (end_on IS NOT NULL AND end_on < start_on) THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE = '22023';
    END IF;

    payload_hash := encode(sha256(convert_to(jsonb_build_object(
        'expectedVersion',p_expected_version,'campaign',p_campaign)::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text || p_operation_id::text,0));
    PERFORM 1 FROM public.workspaces
        WHERE id=v_workspace_id AND owner_user_id=actor AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
    -- Farm lock serializes the ACTIVE/HARVEST invariant with the existing create RPC.
    PERFORM 1 FROM public.farms
        WHERE id=v_farm_id AND workspace_id=v_workspace_id AND deleted_at IS NULL
          AND (creator_user_id=actor OR owner_id=actor) FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
    SELECT * INTO current_campaign FROM public.campaigns
        WHERE id=v_campaign_id AND workspace_id=v_workspace_id AND farm_id=v_farm_id
          AND deleted_at IS NULL AND snapshot_format=1 FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
    -- A legacy fixture without a real account-bound campaign receipt is not adopted here.
    PERFORM 1 FROM magina_g2_private.sync_operations
        WHERE account_id=actor AND entity_type='CAMPAIGN' AND entity_id=v_campaign_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;

    SELECT * INTO receipt FROM magina_g2_private.sync_operations
        WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF receipt.entity_type <> 'CAMPAIGN' OR receipt.entity_id <> v_campaign_id
           OR receipt.payload_hash <> payload_hash THEN
            RAISE EXCEPTION 'campaign conflict' USING ERRCODE='23505';
        END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,
            'entity_id',v_campaign_id,'status',receipt.status,
            'remote_version',receipt.result_version,'payload_version',receipt.payload_version,
            'idempotent',true);
    END IF;
    IF current_campaign.version <> p_expected_version THEN
        RAISE EXCEPTION 'campaign stale version' USING ERRCODE='23505';
    END IF;
    IF p_campaign->>'status' IN ('ACTIVE','HARVEST') AND EXISTS (
        SELECT 1 FROM public.campaigns
        WHERE farm_id=v_farm_id AND id<>v_campaign_id AND deleted_at IS NULL
          AND status IN ('ACTIVE','HARVEST')) THEN
        RAISE EXCEPTION 'campaign conflict' USING ERRCODE='23505';
    END IF;
    IF p_campaign->>'status' <> 'PREPARATION'
       AND NOT EXISTS (SELECT 1 FROM public.campaign_parcels WHERE campaign_id=v_campaign_id) THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
    END IF;
    UPDATE public.campaigns SET name=p_campaign->>'name',start_date=start_on,
        end_date=end_on,status=p_campaign->>'status',notes=p_campaign->>'notes',
        updated_at=now(),version=p_expected_version+1
    WHERE id=v_campaign_id AND version=p_expected_version;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign stale version' USING ERRCODE='23505'; END IF;
    INSERT INTO magina_g2_private.sync_operations(
        account_id,operation_id,entity_type,entity_id,payload_hash,result_version,payload_version)
    VALUES(actor,p_operation_id,'CAMPAIGN',v_campaign_id,payload_hash,p_expected_version+1,1)
    RETURNING * INTO receipt;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,
        'entity_id',v_campaign_id,'status',receipt.status,
        'remote_version',receipt.result_version,'payload_version',receipt.payload_version,
        'idempotent',false);
END;
$$;
REVOKE ALL ON FUNCTION public.update_own_campaign_v1(uuid,bigint,jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.update_own_campaign_v1(uuid,bigint,jsonb) TO authenticated;
