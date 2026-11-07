-- CR-015 P4 proposal: per-farm agricultural cycle sync. NO DEPLOY without a separate authorization.
-- Loads after G0, G1c, G2.1 (sync_operations), G2.6 workspace authorization and campaign CAS.
-- Scope: cycle headers and recollection (campaign) links. Activity and expense links stay local
-- because those records are not synchronized yet. Owner-only, like the G2.6 campaign CAS.
-- Rules: per-farm CAS revision, idempotent receipts, a closed cycle is never reopened or changed,
-- a recollection never leaves a closed cycle and no link is ever removed.

CREATE TABLE public.agricultural_cycles (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    farm_id UUID NOT NULL,
    label TEXT NOT NULL CHECK (length(label) BETWEEN 1 AND 80 AND btrim(label) <> ''),
    start_date DATE NOT NULL,
    effective_end_date DATE,
    closed_at TIMESTAMPTZ,
    no_recollection BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_agricultural_cycles_farm FOREIGN KEY (farm_id, workspace_id)
        REFERENCES public.farms(id, workspace_id) ON DELETE RESTRICT,
    CONSTRAINT uq_agricultural_cycles_id_farm UNIQUE (id, farm_id),
    CONSTRAINT ck_agricultural_cycles_interval CHECK (effective_end_date IS NULL OR effective_end_date >= start_date),
    CONSTRAINT ck_agricultural_cycles_closed CHECK ((effective_end_date IS NULL) = (closed_at IS NULL))
);
CREATE INDEX idx_agricultural_cycles_farm ON public.agricultural_cycles(farm_id, start_date);

CREATE TABLE public.agricultural_cycle_recollections (
    campaign_id UUID PRIMARY KEY,
    farm_id UUID NOT NULL,
    cycle_id UUID NOT NULL,
    CONSTRAINT fk_agricultural_cycle_recollections_cycle FOREIGN KEY (cycle_id, farm_id)
        REFERENCES public.agricultural_cycles(id, farm_id) ON DELETE RESTRICT,
    CONSTRAINT fk_agricultural_cycle_recollections_campaign FOREIGN KEY (campaign_id, farm_id)
        REFERENCES public.campaigns(id, farm_id) ON DELETE RESTRICT
);
CREATE INDEX idx_agricultural_cycle_recollections_cycle ON public.agricultural_cycle_recollections(cycle_id);

CREATE TABLE public.agricultural_cycle_farm_heads (
    farm_id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_agricultural_cycle_farm_heads_farm FOREIGN KEY (farm_id, workspace_id)
        REFERENCES public.farms(id, workspace_id) ON DELETE RESTRICT
);

-- No direct table access: reads and writes only through the owner-checked RPCs below.
ALTER TABLE public.agricultural_cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agricultural_cycle_recollections ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.agricultural_cycle_farm_heads ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.agricultural_cycles, public.agricultural_cycle_recollections,
    public.agricultural_cycle_farm_heads FROM PUBLIC, anon, authenticated;

CREATE FUNCTION magina_g2_private.cycle_actor()
RETURNS uuid LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE actor uuid := auth.uid(); claims jsonb := auth.jwt();
BEGIN
    IF actor IS NULL OR claims IS NULL
       OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text
       OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR coalesce(claims->>'is_anonymous','false') <> 'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
       OR (claims->>'exp') !~ '^[0-9]+$'
       OR (claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'cycles denied' USING ERRCODE = '42501';
    END IF;
    RETURN actor;
END;
$$;
REVOKE ALL ON FUNCTION magina_g2_private.cycle_actor() FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.put_own_farm_cycles_v1(
    p_operation_id uuid, p_farm_id uuid, p_expected_revision bigint, p_state jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    actor uuid := magina_g2_private.cycle_actor();
    v_workspace uuid;
    payload_hash text;
    receipt magina_g2_private.sync_operations%ROWTYPE;
    head_revision bigint;
    item jsonb;
    old_cycle public.agricultural_cycles%ROWTYPE;
    old_link public.agricultural_cycle_recollections%ROWTYPE;
    v_id uuid; v_cycle uuid; v_campaign uuid;
    v_start date; v_end date; v_closed timestamptz; v_label text; v_none boolean;
    open_count int := 0;
BEGIN
    IF p_operation_id IS NULL OR p_farm_id IS NULL OR p_expected_revision IS NULL
       OR p_expected_revision < 0 OR p_expected_revision >= 9223372036854775806
       OR p_state IS NULL OR jsonb_typeof(p_state) <> 'object'
       OR octet_length(p_state::text) > 262144
       OR (SELECT count(*) FROM jsonb_object_keys(p_state)) <> 3
       OR NOT p_state ?& ARRAY['workspaceId','cycles','recollections']
       OR jsonb_typeof(p_state->'workspaceId') IS DISTINCT FROM 'string'
       OR jsonb_typeof(p_state->'cycles') IS DISTINCT FROM 'array'
       OR jsonb_typeof(p_state->'recollections') IS DISTINCT FROM 'array'
       OR jsonb_array_length(p_state->'cycles') > 200
       OR jsonb_array_length(p_state->'recollections') > 2000 THEN
        RAISE EXCEPTION 'cycles invalid' USING ERRCODE = '22023';
    END IF;

    -- Parse and validate the proposed state into transaction-local tables before any lock.
    CREATE TEMP TABLE IF NOT EXISTS pg_temp.p4_cycles(id uuid PRIMARY KEY, label text, start_date date,
        end_date date, closed_at timestamptz, no_recollection boolean) ON COMMIT DROP;
    CREATE TEMP TABLE IF NOT EXISTS pg_temp.p4_links(campaign_id uuid PRIMARY KEY, cycle_id uuid) ON COMMIT DROP;
    DELETE FROM pg_temp.p4_cycles; DELETE FROM pg_temp.p4_links;
    BEGIN
        v_workspace := (p_state->>'workspaceId')::uuid;
        FOR item IN SELECT value FROM jsonb_array_elements(p_state->'cycles') LOOP
            IF jsonb_typeof(item) <> 'object' OR (SELECT count(*) FROM jsonb_object_keys(item)) <> 6
               OR NOT item ?& ARRAY['id','label','startDate','effectiveEndDate','closedAt','noRecollection']
               OR jsonb_typeof(item->'id') <> 'string' OR jsonb_typeof(item->'label') <> 'string'
               OR jsonb_typeof(item->'startDate') <> 'string'
               OR jsonb_typeof(item->'effectiveEndDate') NOT IN ('string','null')
               OR jsonb_typeof(item->'closedAt') NOT IN ('string','null')
               OR jsonb_typeof(item->'noRecollection') <> 'boolean'
               OR item->>'startDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
               OR coalesce(item->>'effectiveEndDate','2000-01-01') !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN
                RAISE invalid_parameter_value;
            END IF;
            v_label := item->>'label';
            v_start := (item->>'startDate')::date;
            v_end := (item->>'effectiveEndDate')::date;
            v_closed := (item->>'closedAt')::timestamptz;
            IF length(v_label) NOT BETWEEN 1 AND 80 OR btrim(v_label) = '' OR btrim(v_label) <> v_label
               OR (v_end IS NULL) <> (v_closed IS NULL) OR v_end < v_start THEN
                RAISE invalid_parameter_value;
            END IF;
            INSERT INTO pg_temp.p4_cycles VALUES ((item->>'id')::uuid, v_label, v_start, v_end, v_closed,
                (item->>'noRecollection')::boolean);
        END LOOP;
        FOR item IN SELECT value FROM jsonb_array_elements(p_state->'recollections') LOOP
            IF jsonb_typeof(item) <> 'object' OR (SELECT count(*) FROM jsonb_object_keys(item)) <> 2
               OR NOT item ?& ARRAY['campaignId','cycleId']
               OR jsonb_typeof(item->'campaignId') <> 'string' OR jsonb_typeof(item->'cycleId') <> 'string' THEN
                RAISE invalid_parameter_value;
            END IF;
            INSERT INTO pg_temp.p4_links VALUES ((item->>'campaignId')::uuid, (item->>'cycleId')::uuid);
        END LOOP;
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow OR invalid_datetime_format
        OR invalid_parameter_value OR unique_violation THEN
        RAISE EXCEPTION 'cycles invalid' USING ERRCODE = '22023';
    END;
    SELECT count(*) INTO open_count FROM pg_temp.p4_cycles WHERE end_date IS NULL;
    IF open_count > 1
       OR EXISTS (SELECT 1 FROM pg_temp.p4_cycles a JOIN pg_temp.p4_cycles b ON a.id < b.id
                  WHERE a.start_date <= coalesce(b.end_date,'infinity'::date)
                    AND b.start_date <= coalesce(a.end_date,'infinity'::date))
       OR EXISTS (SELECT 1 FROM pg_temp.p4_links l WHERE NOT EXISTS
                  (SELECT 1 FROM pg_temp.p4_cycles c WHERE c.id = l.cycle_id))
       OR EXISTS (SELECT 1 FROM pg_temp.p4_links l JOIN pg_temp.p4_cycles c ON c.id = l.cycle_id
                  WHERE c.no_recollection) THEN
        RAISE EXCEPTION 'cycles invalid' USING ERRCODE = '22023';
    END IF;

    payload_hash := encode(sha256(convert_to(jsonb_build_object('farmId',p_farm_id,
        'expectedRevision',p_expected_revision,'state',p_state)::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text || p_operation_id::text,0));
    PERFORM 1 FROM public.workspaces
        WHERE id = v_workspace AND owner_user_id = actor AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'cycles denied' USING ERRCODE = '42501'; END IF;
    -- The farm row lock serializes every cycle change of this farm.
    PERFORM 1 FROM public.farms
        WHERE id = p_farm_id AND workspace_id = v_workspace AND deleted_at IS NULL
          AND (creator_user_id = actor OR owner_id = actor) FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'cycles denied' USING ERRCODE = '42501'; END IF;

    SELECT * INTO receipt FROM magina_g2_private.sync_operations
        WHERE account_id = actor AND operation_id = p_operation_id;
    IF FOUND THEN
        IF receipt.entity_type <> 'FARM_CYCLES' OR receipt.entity_id <> p_farm_id
           OR receipt.payload_hash <> payload_hash THEN
            RAISE EXCEPTION 'cycles conflict: OPERATION_REUSED' USING ERRCODE = '23505';
        END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',p_farm_id,
            'status',receipt.status,'remote_version',receipt.result_version,
            'payload_version',receipt.payload_version,'idempotent',true);
    END IF;

    SELECT revision INTO head_revision FROM public.agricultural_cycle_farm_heads WHERE farm_id = p_farm_id;
    IF coalesce(head_revision,0) <> p_expected_revision THEN
        RAISE EXCEPTION 'cycles conflict: STALE_REVISION' USING ERRCODE = '23505';
    END IF;
    IF EXISTS (SELECT 1 FROM public.agricultural_cycles g JOIN pg_temp.p4_cycles c ON c.id = g.id
               WHERE g.farm_id <> p_farm_id) THEN
        RAISE EXCEPTION 'cycles conflict: CYCLE_ID_TAKEN' USING ERRCODE = '23505';
    END IF;
    -- History is kept: no cycle disappears, identity fields never change, closed cycles are frozen.
    FOR old_cycle IN SELECT * FROM public.agricultural_cycles WHERE farm_id = p_farm_id LOOP
        SELECT id, label, start_date, end_date, closed_at, no_recollection
            INTO v_id, v_label, v_start, v_end, v_closed, v_none
            FROM pg_temp.p4_cycles WHERE id = old_cycle.id;
        IF NOT FOUND THEN RAISE EXCEPTION 'cycles conflict: CYCLE_REMOVED' USING ERRCODE = '23505'; END IF;
        IF v_label <> old_cycle.label OR v_start <> old_cycle.start_date THEN
            RAISE EXCEPTION 'cycles conflict: CYCLE_CHANGED' USING ERRCODE = '23505';
        END IF;
        IF old_cycle.effective_end_date IS NOT NULL AND (v_end IS DISTINCT FROM old_cycle.effective_end_date
           OR v_closed IS DISTINCT FROM old_cycle.closed_at OR v_none IS DISTINCT FROM old_cycle.no_recollection) THEN
            RAISE EXCEPTION 'cycles conflict: CLOSED_CYCLE_CHANGED' USING ERRCODE = '23505';
        END IF;
    END LOOP;
    FOR old_link IN SELECT * FROM public.agricultural_cycle_recollections WHERE farm_id = p_farm_id LOOP
        SELECT cycle_id INTO v_cycle FROM pg_temp.p4_links WHERE campaign_id = old_link.campaign_id;
        IF NOT FOUND THEN RAISE EXCEPTION 'cycles conflict: LINK_REMOVED' USING ERRCODE = '23505'; END IF;
        IF v_cycle <> old_link.cycle_id AND EXISTS (SELECT 1 FROM public.agricultural_cycles
           WHERE id = old_link.cycle_id AND effective_end_date IS NOT NULL) THEN
            RAISE EXCEPTION 'cycles conflict: RECOLLECTION_LEFT_CLOSED_CYCLE' USING ERRCODE = '23505';
        END IF;
    END LOOP;
    -- Parents first: every linked recollection must already be on the server, in this farm.
    IF EXISTS (SELECT 1 FROM pg_temp.p4_links l WHERE NOT EXISTS (SELECT 1 FROM public.campaigns c
               WHERE c.id = l.campaign_id AND c.farm_id = p_farm_id AND c.workspace_id = v_workspace
                 AND c.deleted_at IS NULL)) THEN
        RAISE EXCEPTION 'cycles conflict: PARENT_NOT_SYNCED' USING ERRCODE = '23505';
    END IF;
    -- A closed cycle holds only finished recollections (checked on what changes in this operation).
    IF EXISTS (SELECT 1 FROM pg_temp.p4_links l JOIN pg_temp.p4_cycles c ON c.id = l.cycle_id
               JOIN public.campaigns k ON k.id = l.campaign_id
               LEFT JOIN public.agricultural_cycles g ON g.id = c.id
               LEFT JOIN public.agricultural_cycle_recollections o ON o.campaign_id = l.campaign_id
               WHERE c.end_date IS NOT NULL AND k.status <> 'CLOSED'
                 AND (g.effective_end_date IS NULL OR o.cycle_id IS DISTINCT FROM l.cycle_id)) THEN
        RAISE EXCEPTION 'cycles conflict: RECOLLECTION_NOT_CLOSED' USING ERRCODE = '23505';
    END IF;

    INSERT INTO public.agricultural_cycles(id,workspace_id,farm_id,label,start_date,effective_end_date,
        closed_at,no_recollection)
    SELECT c.id,v_workspace,p_farm_id,c.label,c.start_date,c.end_date,c.closed_at,c.no_recollection
    FROM pg_temp.p4_cycles c WHERE NOT EXISTS (SELECT 1 FROM public.agricultural_cycles g WHERE g.id = c.id);
    UPDATE public.agricultural_cycles g SET effective_end_date = c.end_date, closed_at = c.closed_at,
        no_recollection = c.no_recollection, version = g.version + 1, updated_at = now()
    FROM pg_temp.p4_cycles c
    WHERE g.id = c.id AND g.effective_end_date IS NULL AND c.end_date IS NOT NULL;
    INSERT INTO public.agricultural_cycle_recollections(campaign_id,farm_id,cycle_id)
    SELECT l.campaign_id,p_farm_id,l.cycle_id FROM pg_temp.p4_links l
    ON CONFLICT (campaign_id) DO UPDATE SET cycle_id = EXCLUDED.cycle_id;
    INSERT INTO public.agricultural_cycle_farm_heads(farm_id,workspace_id,revision)
    VALUES (p_farm_id,v_workspace,p_expected_revision+1)
    ON CONFLICT (farm_id) DO UPDATE SET revision = EXCLUDED.revision, updated_at = now();
    INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,
        payload_hash,result_version,payload_version)
    VALUES (actor,p_operation_id,'FARM_CYCLES',p_farm_id,payload_hash,p_expected_revision+1,1)
    RETURNING * INTO receipt;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',p_farm_id,
        'status',receipt.status,'remote_version',receipt.result_version,
        'payload_version',receipt.payload_version,'idempotent',false);
END;
$$;
REVOKE ALL ON FUNCTION public.put_own_farm_cycles_v1(uuid,uuid,bigint,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.put_own_farm_cycles_v1(uuid,uuid,bigint,jsonb) TO authenticated;

CREATE FUNCTION public.get_own_farm_cycles_v1(p_farm_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE actor uuid := magina_g2_private.cycle_actor(); v_workspace uuid;
BEGIN
    SELECT f.workspace_id INTO v_workspace FROM public.farms f JOIN public.workspaces w ON w.id = f.workspace_id
        WHERE f.id = p_farm_id AND f.deleted_at IS NULL AND w.deleted_at IS NULL AND w.owner_user_id = actor
          AND (f.creator_user_id = actor OR f.owner_id = actor);
    IF NOT FOUND THEN RAISE EXCEPTION 'cycles denied' USING ERRCODE = '42501'; END IF;
    RETURN jsonb_build_object('contract_version',1,'subject',actor,'farm_id',p_farm_id,'workspace_id',v_workspace,
        'revision',coalesce((SELECT revision FROM public.agricultural_cycle_farm_heads WHERE farm_id = p_farm_id),0),
        'cycles',coalesce((SELECT jsonb_agg(jsonb_build_object('id',id,'label',label,'startDate',start_date,
            'effectiveEndDate',effective_end_date,
            'closedAt',to_char(closed_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
            'noRecollection',no_recollection) ORDER BY start_date,id)
            FROM public.agricultural_cycles WHERE farm_id = p_farm_id),'[]'::jsonb),
        'recollections',coalesce((SELECT jsonb_agg(jsonb_build_object('campaignId',campaign_id,'cycleId',cycle_id)
            ORDER BY campaign_id) FROM public.agricultural_cycle_recollections WHERE farm_id = p_farm_id),'[]'::jsonb));
END;
$$;
REVOKE ALL ON FUNCTION public.get_own_farm_cycles_v1(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_own_farm_cycles_v1(uuid) TO authenticated;
