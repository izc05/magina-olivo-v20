-- Mágina Olivo — G2.1 Proposal: Idempotent Own-Data Sync & Farm Isolation
-- Candidate proposal only; not a migration or deployment artifact.
-- NO DEPLOY: Ephemeral native testing only. Never deploy to production or persistent DEV.
-- Current disposable bootstrap loads G1c, this file and g2-parcel-bootstrap.sql before any RPC.

CREATE SCHEMA IF NOT EXISTS magina_g2_private;
REVOKE ALL ON SCHEMA magina_g2_private FROM PUBLIC, anon, authenticated;

-- 1. Idempotency store for synchronization operations
CREATE TABLE IF NOT EXISTS magina_g2_private.sync_operations (
    account_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    operation_id UUID NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    payload_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'APPLIED',
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, operation_id)
);

ALTER TABLE magina_g2_private.sync_operations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON magina_g2_private.sync_operations FROM PUBLIC, anon, authenticated;

-- Enforce immutability of recorded sync operations
CREATE OR REPLACE FUNCTION magina_g2_private.sync_operations_immutable()
RETURNS trigger LANGUAGE plpgsql SET search_path='' AS $$
BEGIN
    RAISE EXCEPTION 'sync operation record is immutable';
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_operations_immutable ON magina_g2_private.sync_operations;
CREATE TRIGGER trg_sync_operations_immutable
    BEFORE UPDATE OR DELETE ON magina_g2_private.sync_operations
    FOR EACH ROW EXECUTE FUNCTION magina_g2_private.sync_operations_immutable();

-- 2. Ensure revoked_at is available on farm_access_grants and evaluated immediately
ALTER TABLE public.farm_access_grants ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION magina_private.has_farm_capability_for(f_id UUID, req_cap TEXT, actor UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
    SELECT EXISTS (
        -- Farm owner/creator always has full capability
        SELECT 1 FROM public.farms
        WHERE id = f_id AND (
            creator_user_id = actor
            OR owner_id = actor
            OR (workspace_id IS NOT NULL AND workspace_id IN (
                SELECT id FROM public.workspaces WHERE owner_user_id = actor AND deleted_at IS NULL
            ))
        ) AND deleted_at IS NULL
        UNION
        -- Active grant with requested capability (immediate revocation check)
        SELECT 1 FROM public.farm_access_grants
        WHERE farm_id = f_id
          AND user_id = actor
          AND status = 'ACTIVE'
          AND revoked_at IS NULL
          AND valid_from <= now()
          AND (valid_until IS NULL OR valid_until > now())
          AND (req_cap = ANY(capabilities) OR 'manage_all' = ANY(capabilities))
    );
$$;


-- Complete v2 parcel snapshots. Proposal only: created exclusively in disposable tests.
ALTER TABLE public.deliveries ADD COLUMN IF NOT EXISTS snapshot_format INTEGER NOT NULL DEFAULT 1;
ALTER TABLE magina_g2_private.sync_operations ADD COLUMN IF NOT EXISTS payload_version INTEGER NOT NULL DEFAULT 1;
CREATE TABLE IF NOT EXISTS public.delivery_parcels (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    parcel_id UUID NOT NULL,
    campaign_parcel_id UUID,
    parcel_name_at_delivery TEXT NOT NULL,
    weight_grams BIGINT,
    allocation_mode TEXT NOT NULL,
    UNIQUE(delivery_id, parcel_id),
    FOREIGN KEY(delivery_id, workspace_id) REFERENCES public.deliveries(id, workspace_id),
    FOREIGN KEY(parcel_id, workspace_id) REFERENCES public.parcels(id, workspace_id),
    CHECK ((allocation_mode='EXACT' AND weight_grams IS NOT NULL AND weight_grams>0) OR
           (allocation_mode='UNALLOCATED' AND weight_grams IS NULL))
);
ALTER TABLE public.delivery_parcels ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.delivery_parcels FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.delivery_parcels TO authenticated;
DROP POLICY IF EXISTS delivery_parcels_read ON public.delivery_parcels;
CREATE POLICY delivery_parcels_read ON public.delivery_parcels FOR SELECT TO authenticated
USING (coalesce((auth.jwt()->>'is_anonymous')::boolean, false)=false AND EXISTS (
    SELECT 1 FROM public.deliveries d WHERE d.id=delivery_id AND d.workspace_id=delivery_parcels.workspace_id
    AND d.deleted_at IS NULL AND public.has_farm_capability(d.farm_id,'view_deliveries')
));

-- 3. Idempotent RPC: Sync Own Delivery (Pesada de aceituna)
CREATE OR REPLACE FUNCTION magina_g2_private.sync_delivery(p_operation_id UUID, p_delivery JSONB, p_snapshot_version INTEGER)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    actor UUID := auth.uid();
    v_claims JSONB;
    v_delivery_id UUID;
    v_farm_id UUID;
    v_campaign_id UUID;
    v_delivery_date DATE;
    v_destination_name TEXT;
    v_net_grams BIGINT;
    v_gross_grams BIGINT;
    v_tare_grams BIGINT;
    v_delivery_number TEXT;
    v_ticket_number TEXT;
    v_source TEXT;
    v_notes TEXT;
    v_harvest_id UUID;
    v_delivery_time TEXT;
    v_harvest_origin TEXT;
    v_workspace UUID;
    v_share JSONB;
    v_parcel UUID;
    v_child_ids UUID[] := ARRAY[]::UUID[];
    v_parcel_ids UUID[] := ARRAY[]::UUID[];
    v_known_grams NUMERIC := 0;
    v_unknown_count INTEGER := 0;
    v_destination_org UUID;
    v_expected_workspace UUID;
    v_payload_hash TEXT;
    v_result_version BIGINT;
    v_saved magina_g2_private.sync_operations%ROWTYPE;
    v_existing_delivery public.deliveries%ROWTYPE;
BEGIN
    IF actor IS NULL THEN
        RAISE EXCEPTION 'sync denied: unauthenticated' USING ERRCODE = '42501';
    END IF;

    -- Validate JWT claims if present
    v_claims := current_setting('request.jwt.claims', true)::jsonb;
    IF v_claims IS NOT NULL THEN
        IF v_claims->>'role' IS DISTINCT FROM 'authenticated'
           OR v_claims->>'sub' IS DISTINCT FROM actor::text
           OR coalesce(v_claims->>'is_anonymous', 'false') <> 'false'
           OR jsonb_typeof(v_claims->'exp') IS DISTINCT FROM 'number'
           OR (v_claims->>'exp') !~ '^[0-9]+$' THEN
            RAISE EXCEPTION 'sync denied: invalid token claims' USING ERRCODE = '42501';
        END IF;
        IF (v_claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
            RAISE EXCEPTION 'sync denied: token expired' USING ERRCODE = '42501';
        END IF;
    END IF;

    IF p_operation_id IS NULL OR p_delivery IS NULL OR jsonb_typeof(p_delivery) <> 'object' THEN
        RAISE EXCEPTION 'sync invalid: missing operation or payload' USING ERRCODE = '22023';
    END IF;

    -- This RPC is the v1 header-only contract. A v2 aggregate must never be
    -- acknowledged while its parcel allocations/organization binding are discarded.
    IF p_snapshot_version NOT IN (1, 2) THEN
        RAISE EXCEPTION 'sync unsupported: snapshot version' USING ERRCODE = '22023';
    END IF;
    IF p_snapshot_version = 1 AND (p_delivery ? 'parcels' OR p_delivery ? 'destinationOrganizationId'
       OR p_delivery ? 'payloadVersion') THEN
        RAISE EXCEPTION 'sync unsupported: complete delivery snapshot requires a versioned aggregate contract'
            USING ERRCODE = '22023';
    END IF;

    -- Extract and validate required delivery fields
    BEGIN
        v_delivery_id := (p_delivery->>'id')::uuid;
        v_farm_id := (p_delivery->>'farmId')::uuid;
        v_campaign_id := (p_delivery->>'campaignId')::uuid;
        v_delivery_date := (p_delivery->>'deliveryDate')::date;
        v_destination_name := p_delivery->>'destinationName';
        v_net_grams := (p_delivery->>'netGrams')::bigint;
    EXCEPTION WHEN OTHERS THEN
        RAISE EXCEPTION 'sync invalid: field type conversion error' USING ERRCODE = '22023';
    END;

    IF v_delivery_id IS NULL OR v_farm_id IS NULL OR v_campaign_id IS NULL
       OR v_delivery_date IS NULL OR v_destination_name IS NULL OR v_net_grams IS NULL THEN
        RAISE EXCEPTION 'sync invalid: required fields missing' USING ERRCODE = '22023';
    END IF;

    -- Extract optional fields
    v_gross_grams := (p_delivery->>'grossGrams')::bigint;
    v_tare_grams := (p_delivery->>'tareGrams')::bigint;
    v_delivery_number := p_delivery->>'deliveryNumber';
    v_ticket_number := p_delivery->>'ticketNumber';
    v_source := coalesce(p_delivery->>'source', 'MANUAL');
    v_notes := p_delivery->>'notes';
    v_harvest_id := (p_delivery->>'harvestId')::uuid;
    v_delivery_time := p_delivery->>'deliveryTime';
    v_harvest_origin := p_delivery->>'harvestOrigin';

    v_payload_hash := encode(sha256(convert_to(p_delivery::text, 'UTF8')), 'hex');

    -- Concurrency control: Lock the operation slot for this actor
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text || p_operation_id::text, 0));

    -- Check idempotency
    SELECT * INTO v_saved
    FROM magina_g2_private.sync_operations
    WHERE account_id = actor AND operation_id = p_operation_id;

    IF FOUND THEN
        IF v_saved.entity_type <> 'DELIVERY' OR v_saved.entity_id <> v_delivery_id OR v_saved.payload_hash <> v_payload_hash THEN
            RAISE EXCEPTION 'sync conflict: operation payload mismatch' USING ERRCODE = '23505';
        END IF;
        RETURN jsonb_build_object(
            'success', true,
            'operation_id', p_operation_id,
            'entity_id', v_saved.entity_id,
            'status', v_saved.status,
            'remote_version', v_saved.result_version,
            'payload_version', v_saved.payload_version,
            'idempotent', true
        );
    END IF;

    -- Validate that the farm belongs to the actor (Own-data sync: must be owner/creator of farm)
    -- Lock workspace before farm, then require the farm to retain that workspace.
    SELECT workspace_id INTO v_expected_workspace FROM public.farms WHERE id=v_farm_id;
    PERFORM 1 FROM public.workspaces
      WHERE id=v_expected_workspace AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: actor does not own farm' USING ERRCODE='42501';
    END IF;
    SELECT workspace_id INTO v_workspace
    FROM public.farms
    WHERE id = v_farm_id AND workspace_id=v_expected_workspace
      AND (
          creator_user_id = actor
          OR owner_id = actor
          OR (workspace_id IS NOT NULL AND workspace_id IN (
              SELECT id FROM public.workspaces WHERE owner_user_id = actor AND deleted_at IS NULL
          ))
      )
      AND deleted_at IS NULL FOR SHARE;

    IF v_workspace IS NULL THEN
        RAISE EXCEPTION 'sync denied: actor does not own farm' USING ERRCODE = '42501';
    END IF;

    -- Validate campaign linkage to farm and workspace
    PERFORM 1 FROM public.campaigns
    WHERE id = v_campaign_id AND farm_id = v_farm_id AND workspace_id = v_workspace AND deleted_at IS NULL FOR SHARE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync invalid: campaign does not belong to farm' USING ERRCODE = '22023';
    END IF;

    IF p_snapshot_version = 2 THEN
        IF jsonb_typeof(p_delivery->'id') IS DISTINCT FROM 'string'
           OR jsonb_typeof(p_delivery->'farmId') IS DISTINCT FROM 'string'
           OR jsonb_typeof(p_delivery->'campaignId') IS DISTINCT FROM 'string'
           OR jsonb_typeof(p_delivery->'deliveryDate') IS DISTINCT FROM 'string'
           OR jsonb_typeof(p_delivery->'destinationName') IS DISTINCT FROM 'string'
           OR length(btrim(p_delivery->>'destinationName')) NOT BETWEEN 1 AND 512
           OR jsonb_typeof(p_delivery->'netGrams') IS DISTINCT FROM 'number'
           OR (p_delivery->>'netGrams') !~ '^[0-9]+$'
           OR v_net_grams<=0 THEN
            RAISE EXCEPTION 'sync invalid: v2 required header types or weight' USING ERRCODE='22023';
        END IF;
        IF EXISTS (SELECT 1 FROM jsonb_each(p_delivery) AS f(key,value)
            WHERE key IN ('destinationOrganizationId','deliveryNumber','ticketNumber','source','notes',
                'harvestId','deliveryTime','harvestOrigin')
              AND jsonb_typeof(value) NOT IN ('string','null')) THEN
            RAISE EXCEPTION 'sync invalid: v2 optional text type' USING ERRCODE='22023';
        END IF;
        IF EXISTS (SELECT 1 FROM jsonb_each(p_delivery) AS f(key,value)
            WHERE key IN ('grossGrams','tareGrams','baseVersion')
              AND jsonb_typeof(value)<>'null'
              AND (jsonb_typeof(value)<>'number' OR value::text !~ '^[0-9]+$')) THEN
            RAISE EXCEPTION 'sync invalid: v2 optional integer type' USING ERRCODE='22023';
        END IF;
        IF (v_gross_grams IS NOT NULL AND v_gross_grams<=0)
           OR (v_tare_grams IS NOT NULL AND v_tare_grams<0)
           OR (v_gross_grams IS NOT NULL AND v_tare_grams IS NOT NULL
               AND v_gross_grams::numeric-v_tare_grams::numeric<>v_net_grams)
           OR v_source NOT IN ('MANUAL','TICKET_OCR')
           OR (v_harvest_origin IS NOT NULL AND v_harvest_origin NOT IN ('TREE','GROUND')) THEN
            RAISE EXCEPTION 'sync invalid: v2 ticket arithmetic or source' USING ERRCODE='22023';
        END IF;
        IF jsonb_typeof(p_delivery->'parcels') IS DISTINCT FROM 'array'
           OR jsonb_array_length(p_delivery->'parcels') NOT BETWEEN 1 AND 1000 THEN
            RAISE EXCEPTION 'sync invalid: parcels array required and bounded' USING ERRCODE='22023';
        END IF;
        IF EXISTS (SELECT 1 FROM jsonb_object_keys(p_delivery) AS k(key)
            WHERE key NOT IN ('id','farmId','campaignId','deliveryDate','destinationName',
                'destinationOrganizationId','netGrams','grossGrams','tareGrams','deliveryNumber',
                'ticketNumber','source','notes','harvestId','deliveryTime','harvestOrigin','baseVersion','parcels')) THEN
            RAISE EXCEPTION 'sync invalid: unsupported delivery field' USING ERRCODE='22023';
        END IF;
        v_destination_org := (p_delivery->>'destinationOrganizationId')::uuid;
        FOR v_share IN SELECT value FROM jsonb_array_elements(p_delivery->'parcels') LOOP
            IF jsonb_typeof(v_share) IS DISTINCT FROM 'object'
               OR NOT (v_share ?& ARRAY['id','parcelId','campaignParcelId','parcelNameAtDelivery','weightGrams','allocationMode'])
               OR EXISTS (SELECT 1 FROM jsonb_object_keys(v_share) AS k(key)
                    WHERE key NOT IN ('id','parcelId','campaignParcelId','parcelNameAtDelivery','weightGrams','allocationMode'))
               OR jsonb_typeof(v_share->'id') IS DISTINCT FROM 'string'
               OR jsonb_typeof(v_share->'parcelId') IS DISTINCT FROM 'string'
               OR jsonb_typeof(v_share->'parcelNameAtDelivery') IS DISTINCT FROM 'string'
               OR length(v_share->>'parcelNameAtDelivery') NOT BETWEEN 1 AND 512
               OR jsonb_typeof(v_share->'allocationMode') IS DISTINCT FROM 'string'
               OR jsonb_typeof(v_share->'campaignParcelId') NOT IN ('string','null') THEN
                RAISE EXCEPTION 'sync invalid: parcel snapshot shape' USING ERRCODE='22023';
            END IF;
            v_parcel := (v_share->>'parcelId')::uuid;
            IF v_parcel = ANY(v_parcel_ids) OR (v_share->>'id')::uuid = ANY(v_child_ids) THEN
                RAISE EXCEPTION 'sync invalid: duplicate parcel snapshot' USING ERRCODE='22023';
            END IF;
            v_parcel_ids := array_append(v_parcel_ids, v_parcel);
            v_child_ids := array_append(v_child_ids, (v_share->>'id')::uuid);
            PERFORM 1 FROM public.parcels p JOIN public.farm_parcel_memberships m
              ON m.parcel_id=p.id AND m.workspace_id=p.workspace_id
              WHERE p.id=v_parcel AND p.workspace_id=v_workspace AND m.farm_id=v_farm_id
                AND p.deleted_at IS NULL AND m.deleted_at IS NULL
                AND (m.valid_until IS NULL OR
                    (CASE WHEN m.valid_until_exact IS NULL THEN extract(epoch FROM m.valid_until)*1000000000
                        ELSE magina_g2_private.instant_key(m.valid_until_exact) END) >
                    (CASE WHEN m.valid_from_exact IS NULL THEN extract(epoch FROM m.valid_from)*1000000000
                        ELSE magina_g2_private.instant_key(m.valid_from_exact) END))
                AND (CASE WHEN m.valid_from_exact IS NULL THEN extract(epoch FROM m.valid_from)*1000000000
                    ELSE magina_g2_private.instant_key(m.valid_from_exact) END)
                    < extract(epoch FROM (v_delivery_date+1)::timestamptz)*1000000000
                AND (m.valid_until IS NULL OR (CASE WHEN m.valid_until_exact IS NULL THEN extract(epoch FROM m.valid_until)*1000000000
                    ELSE magina_g2_private.instant_key(m.valid_until_exact) END)
                    > extract(epoch FROM v_delivery_date::timestamptz)*1000000000)
              FOR SHARE OF p,m;
            IF NOT FOUND THEN
                RAISE EXCEPTION 'sync denied: parcel does not belong to farm' USING ERRCODE='42501';
            END IF;
            PERFORM (v_share->>'campaignParcelId')::uuid;
            IF v_share->>'campaignParcelId' IS NOT NULL AND EXISTS (
                SELECT 1 FROM public.campaigns WHERE id=v_campaign_id AND snapshot_format=1) THEN
                PERFORM 1 FROM public.campaign_parcels WHERE id=(v_share->>'campaignParcelId')::uuid
                    AND campaign_id=v_campaign_id AND parcel_id=v_parcel AND workspace_id=v_workspace FOR SHARE;
                IF NOT FOUND THEN
                    RAISE EXCEPTION 'sync invalid: campaign parcel snapshot mismatch' USING ERRCODE='22023';
                END IF;
            END IF;
            IF v_share->>'allocationMode' = 'UNALLOCATED'
               AND jsonb_typeof(v_share->'weightGrams') = 'null' THEN
                v_unknown_count := v_unknown_count + 1;
            ELSIF v_share->>'allocationMode' = 'EXACT'
               AND jsonb_typeof(v_share->'weightGrams') = 'number'
               AND (v_share->>'weightGrams') ~ '^[0-9]+$'
               AND (v_share->>'weightGrams')::numeric > 0
               AND (v_share->>'weightGrams')::numeric <= 9223372036854775807 THEN
                v_known_grams := v_known_grams + (v_share->>'weightGrams')::numeric;
            ELSE
                RAISE EXCEPTION 'sync invalid: parcel weight or allocation mode' USING ERRCODE='22023';
            END IF;
        END LOOP;
        IF v_known_grams > v_net_grams OR
           (cardinality(v_parcel_ids)>0 AND v_unknown_count=0 AND v_known_grams<>v_net_grams) THEN
            RAISE EXCEPTION 'sync invalid: parcel weights do not reconcile' USING ERRCODE='22023';
        END IF;
    END IF;

    -- Apply delivery upsert with row-level locking and stale update protection
    SELECT * INTO v_existing_delivery
    FROM public.deliveries
    WHERE id = v_delivery_id
    FOR UPDATE;

    IF FOUND THEN
        IF v_existing_delivery.snapshot_format > p_snapshot_version THEN
            RAISE EXCEPTION 'sync conflict: snapshot downgrade forbidden' USING ERRCODE='23505';
        END IF;
        IF p_snapshot_version=2 AND EXISTS (
            SELECT 1 FROM public.delivery_parcels old
            JOIN jsonb_array_elements(p_delivery->'parcels') AS incoming(value)
              ON old.parcel_id=(incoming.value->>'parcelId')::uuid
            WHERE old.delivery_id=v_delivery_id AND (
                old.id<>(incoming.value->>'id')::uuid OR
                old.parcel_name_at_delivery IS DISTINCT FROM incoming.value->>'parcelNameAtDelivery' OR
                old.campaign_parcel_id IS DISTINCT FROM (incoming.value->>'campaignParcelId')::uuid)
        ) THEN
            RAISE EXCEPTION 'sync conflict: historical parcel binding mismatch' USING ERRCODE='23505';
        END IF;
        IF v_existing_delivery.farm_id <> v_farm_id OR v_existing_delivery.workspace_id <> v_workspace THEN
            RAISE EXCEPTION 'sync conflict: delivery immutable binding mismatch' USING ERRCODE = '23505';
        END IF;

        IF v_existing_delivery.campaign_id <> v_campaign_id THEN
            RAISE EXCEPTION 'sync conflict: delivery campaign mismatch' USING ERRCODE = '23505';
        END IF;

        IF NOT (p_delivery ? 'baseVersion') OR p_delivery->>'baseVersion' IS NULL THEN
            RAISE EXCEPTION 'sync conflict: baseVersion required for updating existing delivery' USING ERRCODE = '23505';
        END IF;

        IF v_existing_delivery.version <> (p_delivery->>'baseVersion')::bigint THEN
            RAISE EXCEPTION 'sync conflict: stale delivery version' USING ERRCODE = '23505';
        END IF;

        -- Notice: trg_deliveries_immutability auto-increments version and sets updated_at
        UPDATE public.deliveries SET
            delivery_date = v_delivery_date,
            destination_name = v_destination_name,
            net_grams = v_net_grams,
            gross_grams = v_gross_grams,
            tare_grams = v_tare_grams,
            delivery_number = v_delivery_number,
            ticket_number = v_ticket_number,
            source = v_source,
            notes = v_notes,
            harvest_id = v_harvest_id,
            delivery_time = v_delivery_time,
            harvest_origin = v_harvest_origin,
            destination_organization_id = CASE WHEN p_snapshot_version=2 THEN v_destination_org ELSE destination_organization_id END,
            snapshot_format = p_snapshot_version
        WHERE id = v_delivery_id AND workspace_id = v_workspace AND version = v_existing_delivery.version;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync conflict: delivery concurrent update detected' USING ERRCODE = '23505';
        END IF;
    ELSE
        INSERT INTO public.deliveries (
            id, workspace_id, farm_id, campaign_id, delivery_date, destination_name,
            net_grams, gross_grams, tare_grams, delivery_number, ticket_number,
            source, notes, harvest_id, delivery_time, harvest_origin,
            recorded_by_user_id, created_at, updated_at, version, destination_organization_id, snapshot_format
        ) VALUES (
            v_delivery_id, v_workspace, v_farm_id, v_campaign_id, v_delivery_date, v_destination_name,
            v_net_grams, v_gross_grams, v_tare_grams, v_delivery_number, v_ticket_number,
            v_source, v_notes, v_harvest_id, v_delivery_time, v_harvest_origin,
            actor, now(), now(), 1, v_destination_org, p_snapshot_version
        );
    END IF;

    IF p_snapshot_version=2 THEN
        DELETE FROM public.delivery_parcels WHERE delivery_id=v_delivery_id;
        INSERT INTO public.delivery_parcels(id, workspace_id, delivery_id, parcel_id,
            campaign_parcel_id, parcel_name_at_delivery, weight_grams, allocation_mode)
        SELECT (value->>'id')::uuid, v_workspace, v_delivery_id, (value->>'parcelId')::uuid,
            (value->>'campaignParcelId')::uuid, value->>'parcelNameAtDelivery',
            (value->>'weightGrams')::bigint, value->>'allocationMode'
        FROM jsonb_array_elements(p_delivery->'parcels');
    END IF;

    -- Capture the version committed by this operation; later retries must return this exact value.
    SELECT version INTO v_result_version FROM public.deliveries WHERE id = v_delivery_id;
    IF v_result_version IS NULL THEN
        RAISE EXCEPTION 'sync invalid: applied delivery version missing' USING ERRCODE = '22023';
    END IF;

    -- Record idempotent operation
    INSERT INTO magina_g2_private.sync_operations(
        account_id, operation_id, entity_type, entity_id, payload_hash, status, result_version, payload_version
    ) VALUES (
        actor, p_operation_id, 'DELIVERY', v_delivery_id, v_payload_hash, 'APPLIED', v_result_version, p_snapshot_version
    );

    RETURN jsonb_build_object(
        'success', true,
        'operation_id', p_operation_id,
        'entity_id', v_delivery_id,
        'status', 'APPLIED',
        'remote_version', v_result_version,
        'payload_version', p_snapshot_version,
        'idempotent', false
    );
END;
$$;

-- Public endpoints retain explicit versions; the shared implementation is not exposed.
CREATE OR REPLACE FUNCTION public.sync_own_delivery(p_operation_id UUID, p_delivery JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path=''
AS $$ SELECT magina_g2_private.sync_delivery(p_operation_id,p_delivery,1); $$;
CREATE OR REPLACE FUNCTION public.sync_own_delivery_v2(p_operation_id UUID, p_delivery JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path=''
AS $$ SELECT magina_g2_private.sync_delivery(p_operation_id,p_delivery,2); $$;
REVOKE ALL ON FUNCTION magina_g2_private.sync_delivery(UUID,JSONB,INTEGER) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.sync_own_delivery_v2(UUID,JSONB) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.sync_own_delivery_v2(UUID,JSONB) TO authenticated;

-- One SQL snapshot returns header and historical allocations coherently.
CREATE OR REPLACE FUNCTION public.get_own_delivery_v2(p_delivery_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID := auth.uid(); claims JSONB := current_setting('request.jwt.claims',true)::jsonb; result JSONB;
BEGIN
    IF actor IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text
       OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
       OR (claims->>'exp') !~ '^[0-9]+$'
       OR (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'sync denied: invalid identity' USING ERRCODE='42501';
    END IF;
    SELECT jsonb_build_object('payload_version',2,'delivery',to_jsonb(d),
        'head_payload_hash',(SELECT CASE WHEN count(*)=1 THEN min(o.payload_hash) ELSE NULL END
            FROM magina_g2_private.sync_operations o
            WHERE o.account_id=actor AND o.entity_type='DELIVERY' AND o.entity_id=d.id
              AND o.result_version=d.version AND o.payload_version=2 AND o.status='APPLIED'),
        'parcels',
        coalesce((SELECT jsonb_agg(to_jsonb(p) ORDER BY p.parcel_id)
                  FROM public.delivery_parcels p WHERE p.delivery_id=d.id),'[]'::jsonb))
    INTO result
    FROM public.deliveries d JOIN public.farms f ON f.id=d.farm_id AND f.workspace_id=d.workspace_id
    WHERE d.id=p_delivery_id AND d.deleted_at IS NULL AND d.snapshot_format=2 AND f.deleted_at IS NULL
      AND (f.owner_id=actor OR f.creator_user_id=actor OR EXISTS (
          SELECT 1 FROM public.workspaces w WHERE w.id=f.workspace_id AND w.owner_user_id=actor AND w.deleted_at IS NULL));
    IF result IS NULL THEN
        RAISE EXCEPTION 'sync denied: snapshot unavailable' USING ERRCODE='42501';
    END IF;
    RETURN result;
END; $$;
REVOKE ALL ON FUNCTION public.get_own_delivery_v2(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_own_delivery_v2(UUID) TO authenticated;

-- 4. Idempotent RPC: Sync Own Expense (Gastos contables con protección económica)
CREATE OR REPLACE FUNCTION public.sync_own_expense(p_operation_id UUID, p_expense JSONB)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    actor UUID := auth.uid();
    v_claims JSONB;
    v_expense_id UUID;
    v_farm_id UUID;
    v_campaign_id UUID;
    v_parcel_id UUID;
    v_expense_date DATE;
    v_concept TEXT;
    v_category TEXT;
    v_amount_minor BIGINT;
    v_currency TEXT;
    v_provider TEXT;
    v_notes TEXT;
    v_status TEXT;
    v_workspace UUID;
    v_expected_workspace UUID;
    v_payload_hash TEXT;
    v_result_version BIGINT;
    v_saved magina_g2_private.sync_operations%ROWTYPE;
    v_existing_expense public.expenses%ROWTYPE;
BEGIN
    IF actor IS NULL THEN
        RAISE EXCEPTION 'sync denied: unauthenticated' USING ERRCODE = '42501';
    END IF;

    -- Validate JWT claims if present
    v_claims := current_setting('request.jwt.claims', true)::jsonb;
    IF v_claims IS NOT NULL THEN
        IF v_claims->>'role' IS DISTINCT FROM 'authenticated'
           OR v_claims->>'sub' IS DISTINCT FROM actor::text
           OR coalesce(v_claims->>'is_anonymous', 'false') <> 'false'
           OR jsonb_typeof(v_claims->'exp') IS DISTINCT FROM 'number'
           OR (v_claims->>'exp') !~ '^[0-9]+$' THEN
            RAISE EXCEPTION 'sync denied: invalid token claims' USING ERRCODE = '42501';
        END IF;
        IF (v_claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
            RAISE EXCEPTION 'sync denied: token expired' USING ERRCODE = '42501';
        END IF;
    END IF;

    IF p_operation_id IS NULL OR p_expense IS NULL OR jsonb_typeof(p_expense) <> 'object' THEN
        RAISE EXCEPTION 'sync invalid: missing operation or payload' USING ERRCODE = '22023';
    END IF;

    BEGIN
        v_expense_id := (p_expense->>'id')::uuid;
        v_farm_id := (p_expense->>'farmId')::uuid;
        v_campaign_id := (p_expense->>'campaignId')::uuid;
        v_parcel_id := (p_expense->>'parcelId')::uuid;
        v_expense_date := (p_expense->>'expenseDate')::date;
        v_concept := p_expense->>'concept';
        v_category := p_expense->>'category';
        v_amount_minor := (p_expense->>'amountMinor')::bigint;
        v_currency := coalesce(p_expense->>'currency', 'EUR');
        v_provider := p_expense->>'provider';
        v_notes := p_expense->>'notes';
        v_status := coalesce(p_expense->>'status', 'POSTED');
    EXCEPTION WHEN OTHERS THEN
        RAISE EXCEPTION 'sync invalid: field type conversion error' USING ERRCODE = '22023';
    END;

    IF v_expense_id IS NULL OR v_expense_date IS NULL OR v_concept IS NULL
       OR v_category IS NULL OR v_amount_minor IS NULL THEN
        RAISE EXCEPTION 'sync invalid: required fields missing' USING ERRCODE = '22023';
    END IF;

    v_payload_hash := encode(sha256(convert_to(p_expense::text, 'UTF8')), 'hex');

    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text || p_operation_id::text, 0));

    SELECT * INTO v_saved
    FROM magina_g2_private.sync_operations
    WHERE account_id = actor AND operation_id = p_operation_id;

    IF FOUND THEN
        IF v_saved.entity_type <> 'EXPENSE' OR v_saved.entity_id <> v_expense_id OR v_saved.payload_hash <> v_payload_hash THEN
            RAISE EXCEPTION 'sync conflict: operation payload mismatch' USING ERRCODE = '23505';
        END IF;
        RETURN jsonb_build_object(
            'success', true,
            'operation_id', p_operation_id,
            'entity_id', v_saved.entity_id,
            'status', v_saved.status,
            'remote_version', v_saved.result_version,
            'idempotent', true
        );
    END IF;

    -- Lock workspace before farm so ownership/deletion cannot change after authorization.
    SELECT workspace_id INTO v_expected_workspace FROM public.farms WHERE id = v_farm_id;
    PERFORM 1 FROM public.workspaces
    WHERE id = v_expected_workspace AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync denied: actor does not own farm for economic operations' USING ERRCODE = '42501';
    END IF;

    -- Validate farm ownership (strictly owner/creator of farm)
    SELECT workspace_id INTO v_workspace
    FROM public.farms
    WHERE id = v_farm_id AND workspace_id = v_expected_workspace
      AND (
          creator_user_id = actor
          OR owner_id = actor
          OR (workspace_id IS NOT NULL AND workspace_id IN (
              SELECT id FROM public.workspaces WHERE owner_user_id = actor AND deleted_at IS NULL
          ))
      )
      AND deleted_at IS NULL FOR SHARE;

    IF v_workspace IS NULL THEN
        RAISE EXCEPTION 'sync denied: actor does not own farm for economic operations' USING ERRCODE = '42501';
    END IF;

    -- Validate campaign linkage if provided
    IF v_campaign_id IS NOT NULL THEN
        PERFORM 1 FROM public.campaigns
        WHERE id = v_campaign_id AND farm_id = v_farm_id AND workspace_id = v_workspace AND deleted_at IS NULL FOR SHARE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync invalid: campaign does not belong to farm' USING ERRCODE = '22023';
        END IF;
    END IF;

    -- Validate parcel linkage if provided
    IF v_parcel_id IS NOT NULL THEN
        PERFORM 1 FROM public.parcels p
        JOIN public.farm_parcel_memberships m
          ON m.parcel_id = p.id AND m.workspace_id = p.workspace_id
        WHERE p.id = v_parcel_id AND p.workspace_id = v_workspace AND p.deleted_at IS NULL
          AND m.farm_id = v_farm_id AND m.deleted_at IS NULL
          AND (CASE WHEN m.valid_from_exact IS NULL THEN extract(epoch FROM m.valid_from)*1000000000
                    ELSE magina_g2_private.instant_key(m.valid_from_exact) END)
              < extract(epoch FROM (v_expense_date+1)::timestamptz)*1000000000
          AND (m.valid_until IS NULL OR
               (CASE WHEN m.valid_until_exact IS NULL THEN extract(epoch FROM m.valid_until)*1000000000
                     ELSE magina_g2_private.instant_key(m.valid_until_exact) END)
               > extract(epoch FROM v_expense_date::timestamptz)*1000000000)
          AND (m.valid_until IS NULL OR
               (CASE WHEN m.valid_until_exact IS NULL THEN extract(epoch FROM m.valid_until)*1000000000
                     ELSE magina_g2_private.instant_key(m.valid_until_exact) END)
               > (CASE WHEN m.valid_from_exact IS NULL THEN extract(epoch FROM m.valid_from)*1000000000
                       ELSE magina_g2_private.instant_key(m.valid_from_exact) END))
        FOR SHARE OF p,m;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync denied: parcel does not belong to farm on expense date' USING ERRCODE = '42501';
        END IF;
    END IF;

    -- Apply expense upsert with row-level locking and stale update protection
    SELECT * INTO v_existing_expense
    FROM public.expenses
    WHERE id = v_expense_id
    FOR UPDATE;

    IF FOUND THEN
        IF v_existing_expense.workspace_id <> v_workspace THEN
            RAISE EXCEPTION 'sync denied: expense belongs to another workspace' USING ERRCODE = '42501';
        END IF;

        IF v_existing_expense.farm_id IS NOT NULL AND v_existing_expense.farm_id <> v_farm_id THEN
            RAISE EXCEPTION 'sync conflict: expense farm binding mismatch' USING ERRCODE = '23505';
        END IF;

        IF NOT (p_expense ? 'baseVersion') OR p_expense->>'baseVersion' IS NULL THEN
            RAISE EXCEPTION 'sync conflict: baseVersion required for updating existing expense' USING ERRCODE = '23505';
        END IF;

        IF v_existing_expense.version <> (p_expense->>'baseVersion')::bigint THEN
            RAISE EXCEPTION 'sync conflict: stale expense version' USING ERRCODE = '23505';
        END IF;

        UPDATE public.expenses SET
            farm_id = v_farm_id,
            campaign_id = v_campaign_id,
            parcel_id = v_parcel_id,
            expense_date = v_expense_date,
            concept = v_concept,
            category = v_category,
            amount_minor = v_amount_minor,
            currency = v_currency,
            provider = v_provider,
            notes = v_notes,
            status = v_status,
            version = version + 1,
            updated_at = now()
        WHERE id = v_expense_id AND workspace_id = v_workspace AND version = v_existing_expense.version;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'sync conflict: expense concurrent update detected' USING ERRCODE = '23505';
        END IF;
    ELSE
        INSERT INTO public.expenses (
            id, workspace_id, farm_id, campaign_id, parcel_id, expense_date,
            concept, category, amount_minor, currency, provider, notes,
            status, origin, version, created_at, updated_at
        ) VALUES (
            v_expense_id, v_workspace, v_farm_id, v_campaign_id, v_parcel_id, v_expense_date,
            v_concept, v_category, v_amount_minor, v_currency, v_provider, v_notes,
            v_status, 'MANUAL', 1, now(), now()
        );
    END IF;

    -- Store the exact committed version, including for a later idempotent replay.
    SELECT version INTO v_result_version FROM public.expenses WHERE id = v_expense_id;
    IF v_result_version IS NULL THEN
        RAISE EXCEPTION 'sync invalid: applied expense version missing' USING ERRCODE = '22023';
    END IF;

    INSERT INTO magina_g2_private.sync_operations(
        account_id, operation_id, entity_type, entity_id, payload_hash, status, result_version
    ) VALUES (
        actor, p_operation_id, 'EXPENSE', v_expense_id, v_payload_hash, 'APPLIED', v_result_version
    );

    RETURN jsonb_build_object(
        'success', true,
        'operation_id', p_operation_id,
        'entity_id', v_expense_id,
        'status', 'APPLIED',
        'remote_version', v_result_version,
        'idempotent', false
    );
END;
$$;

-- 5. Offline Recovery Query: Check Confirmed Sync Operations
CREATE OR REPLACE FUNCTION public.check_sync_operations(p_operation_ids UUID[])
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    actor UUID := auth.uid();
    v_confirmed JSONB;
    v_receipts JSONB;
BEGIN
    IF actor IS NULL THEN
        RAISE EXCEPTION 'access denied: unauthenticated' USING ERRCODE = '42501';
    END IF;
    -- Bound the request so a user cannot submit huge recovery arrays to the RPC.
    IF p_operation_ids IS NULL OR cardinality(p_operation_ids) > 200 THEN
        RAISE EXCEPTION 'sync invalid: operation list must have at most 200 entries'
            USING ERRCODE = '22023';
    END IF;

    -- Preserve the legacy list for older clients; include immutable per-operation
    -- committed versions for G2.4 recovery. Never return the CURRENT entity version,
    -- which may now belong to a later operation and would corrupt Android baseVersion.
    SELECT
        coalesce(jsonb_agg(operation_id ORDER BY operation_id), '[]'::jsonb),
        coalesce(jsonb_agg(
            jsonb_build_object(
                'operation_id', operation_id,
                'entity_id', entity_id,
                'entity_type', entity_type,
                'remote_version', result_version,
                'payload_version', payload_version,
                'payload_hash', payload_hash
            ) ORDER BY operation_id
        ), '[]'::jsonb)
    INTO v_confirmed, v_receipts
    FROM magina_g2_private.sync_operations
    WHERE account_id = actor
      AND operation_id = ANY(p_operation_ids);

    RETURN jsonb_build_object(
        'success', true,
        'confirmed_operations', v_confirmed,
        'confirmed_receipts', v_receipts
    );
END;
$$;

-- Function security configuration
REVOKE ALL ON FUNCTION public.sync_own_delivery(UUID, JSONB) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.sync_own_delivery(UUID, JSONB) TO authenticated;

REVOKE ALL ON FUNCTION public.sync_own_expense(UUID, JSONB) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.sync_own_expense(UUID, JSONB) TO authenticated;

REVOKE ALL ON FUNCTION public.check_sync_operations(UUID[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.check_sync_operations(UUID[]) TO authenticated;

-- First resumable bootstrap step. Creation only: never upsert or adopt an existing UUID.
CREATE FUNCTION magina_g2_private.create_workspace(p_operation_id UUID, p_workspace JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID:=auth.uid(); claims JSONB:=auth.jwt(); field TEXT; workspace UUID;
    payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE; replay BOOLEAN:=false;
BEGIN
    IF actor IS NULL OR claims IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text
       OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
       OR (claims->>'exp') !~ '^[0-9]+$' THEN
        RAISE EXCEPTION 'workspace denied' USING ERRCODE='42501';
    END IF;
    IF (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'workspace denied' USING ERRCODE='42501';
    END IF;
    IF p_operation_id IS NULL OR p_workspace IS NULL OR jsonb_typeof(p_workspace)<>'object'
       OR (SELECT count(*) FROM jsonb_object_keys(p_workspace))<>6
       OR NOT p_workspace ?& ARRAY['id','name','countryCode','timezone','locale','currency'] THEN
        RAISE EXCEPTION 'workspace invalid' USING ERRCODE='22023';
    END IF;
    FOREACH field IN ARRAY ARRAY['id','name','countryCode','timezone','locale','currency'] LOOP
        IF jsonb_typeof(p_workspace->field) IS DISTINCT FROM 'string' THEN
            RAISE EXCEPTION 'workspace invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    BEGIN
        workspace:=(p_workspace->>'id')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'workspace invalid' USING ERRCODE='22023';
    END;
    IF length(p_workspace->>'name') NOT BETWEEN 1 AND 200 OR btrim(p_workspace->>'name')=''
       OR (p_workspace->>'countryCode') !~ '^[A-Z]{2}$'
       OR (p_workspace->>'currency') !~ '^[A-Z]{3}$'
       OR length(p_workspace->>'locale')>35 OR (p_workspace->>'locale') !~ '^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$'
       OR length(p_workspace->>'timezone')>64
       OR NOT EXISTS(SELECT 1 FROM pg_catalog.pg_timezone_names WHERE name=p_workspace->>'timezone') THEN
        RAISE EXCEPTION 'workspace invalid' USING ERRCODE='22023';
    END IF;
    payload_hash:=encode(sha256(convert_to(p_workspace::text,'UTF8')),'hex');
    -- Same account/operation lock as the delivery/expense RPCs, including cross-entity collisions.
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    SELECT * INTO saved FROM magina_g2_private.sync_operations
        WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'WORKSPACE' OR saved.entity_id<>workspace OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'workspace conflict' USING ERRCODE='23505';
        END IF;
        -- Replaying a receipt does not restore permission after deletion or ownership change.
        PERFORM 1 FROM public.workspaces WHERE id=workspace AND owner_user_id=actor AND deleted_at IS NULL FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'workspace denied' USING ERRCODE='42501'; END IF;
        replay:=true;
    ELSE
        INSERT INTO public.workspaces(id,owner_user_id,name,country_code,timezone,locale,currency)
        VALUES(workspace,actor,p_workspace->>'name',p_workspace->>'countryCode',
            p_workspace->>'timezone',p_workspace->>'locale',p_workspace->>'currency')
        ON CONFLICT(id) DO NOTHING;
        IF NOT FOUND THEN RAISE EXCEPTION 'workspace conflict' USING ERRCODE='23505'; END IF;
        INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,payload_hash,result_version)
        VALUES(actor,p_operation_id,'WORKSPACE',workspace,payload_hash,1) RETURNING * INTO saved;
    END IF;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',workspace,
        'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',replay);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.create_workspace(UUID,JSONB) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.create_own_workspace(p_operation_id UUID,p_workspace JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.create_workspace(p_operation_id,p_workspace);
$$;
REVOKE ALL ON FUNCTION public.create_own_workspace(UUID,JSONB) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.create_own_workspace(UUID,JSONB) TO authenticated;

-- Requires the reviewed G1c proposal in the SAME disposable database.
-- Opaque local document identity only; no file URI or claim that bytes were uploaded.
ALTER TABLE public.farms ADD COLUMN IF NOT EXISTS cover_document_id UUID;
CREATE FUNCTION magina_g2_private.create_reserved_farm(
    p_operation_id UUID,p_reservation_operation_id UUID,p_reservation_receipt_id UUID,p_farm JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID:=auth.uid(); claims JSONB:=auth.jwt(); field TEXT; farm UUID; workspace UUID; cover UUID;
    token_issuer TEXT; payload_hash TEXT; saved magina_g2_private.sync_operations%ROWTYPE;
    reservation magina_g1c_private.g1c_reservations%ROWTYPE; replay BOOLEAN:=false;
BEGIN
    IF actor IS NULL OR claims IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR (claims->>'exp') !~ '^[0-9]+$' THEN
        RAISE EXCEPTION 'farm denied' USING ERRCODE='42501';
    END IF;
    IF (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'farm denied' USING ERRCODE='42501';
    END IF;
    token_issuer:=claims->>'iss';
    IF token_issuer IS NULL OR token_issuer !~ '^https://[^/?#]+/auth/v1$' THEN
        RAISE EXCEPTION 'farm denied' USING ERRCODE='42501';
    END IF;
    token_issuer:=regexp_replace(token_issuer,'/auth/v1$','');
    IF p_operation_id IS NULL OR p_reservation_operation_id IS NULL OR p_reservation_receipt_id IS NULL
       OR p_farm IS NULL OR jsonb_typeof(p_farm)<>'object'
       OR (SELECT count(*) FROM jsonb_object_keys(p_farm))<>9
       OR NOT p_farm ?& ARRAY['id','workspaceId','name','description','municipality','province','notes','status','coverDocumentId'] THEN
        RAISE EXCEPTION 'farm invalid' USING ERRCODE='22023';
    END IF;
    FOREACH field IN ARRAY ARRAY['id','workspaceId','name','status'] LOOP
        IF jsonb_typeof(p_farm->field) IS DISTINCT FROM 'string' THEN
            RAISE EXCEPTION 'farm invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    FOREACH field IN ARRAY ARRAY['description','municipality','province','notes','coverDocumentId'] LOOP
        IF jsonb_typeof(p_farm->field) NOT IN ('string','null') THEN
            RAISE EXCEPTION 'farm invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    BEGIN
        farm:=(p_farm->>'id')::uuid; workspace:=(p_farm->>'workspaceId')::uuid;
        cover:=(p_farm->>'coverDocumentId')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'farm invalid' USING ERRCODE='22023';
    END;
    IF length(p_farm->>'name') NOT BETWEEN 1 AND 200 OR btrim(p_farm->>'name')=''
       OR p_farm->>'status' NOT IN ('ACTIVE','ARCHIVED')
       OR length(p_farm->>'description')>10000 OR length(p_farm->>'notes')>10000
       OR length(p_farm->>'municipality')>200 OR length(p_farm->>'province')>200 THEN
        RAISE EXCEPTION 'farm invalid' USING ERRCODE='22023';
    END IF;
    payload_hash:=encode(sha256(convert_to(jsonb_build_object('farm',p_farm,
        'reservationOperationId',p_reservation_operation_id,'reservationReceiptId',p_reservation_receipt_id)::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    -- Workspace then farm lock order matches G1c and the INSERT guard.
    PERFORM 1 FROM public.workspaces WHERE id=workspace AND owner_user_id=actor AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'farm denied' USING ERRCODE='42501'; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(farm::text,0));
    SELECT * INTO reservation FROM magina_g1c_private.g1c_reservations
        WHERE actor_id=actor AND operation_id=p_reservation_operation_id
        AND receipt_id=p_reservation_receipt_id AND farm_id=farm
        AND remote_workspace_id=workspace AND issuer=token_issuer;
    IF NOT FOUND THEN RAISE EXCEPTION 'farm denied' USING ERRCODE='42501'; END IF;
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'FARM' OR saved.entity_id<>farm OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'farm conflict' USING ERRCODE='23505';
        END IF;
        PERFORM 1 FROM public.farms WHERE id=farm AND workspace_id=workspace AND creator_user_id=actor
            AND owner_id=actor AND deleted_at IS NULL FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'farm denied' USING ERRCODE='42501'; END IF;
        replay:=true;
    ELSE
        INSERT INTO public.farms(id,workspace_id,creator_user_id,owner_id,name,description,municipality,province,notes,status,cover_document_id)
        VALUES(farm,workspace,actor,actor,p_farm->>'name',p_farm->>'description',p_farm->>'municipality',
            p_farm->>'province',p_farm->>'notes',p_farm->>'status',cover)
        ON CONFLICT(id) DO NOTHING;
        IF NOT FOUND THEN RAISE EXCEPTION 'farm conflict' USING ERRCODE='23505'; END IF;
        INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,payload_hash,result_version)
        VALUES(actor,p_operation_id,'FARM',farm,payload_hash,1) RETURNING * INTO saved;
    END IF;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',farm,
        'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',replay);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.create_reserved_farm(UUID,UUID,UUID,JSONB) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.create_reserved_farm(
    p_operation_id UUID,p_reservation_operation_id UUID,p_reservation_receipt_id UUID,p_farm JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.create_reserved_farm(p_operation_id,p_reservation_operation_id,p_reservation_receipt_id,p_farm);
$$;
REVOKE ALL ON FUNCTION public.create_reserved_farm(UUID,UUID,UUID,JSONB) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.create_reserved_farm(UUID,UUID,UUID,JSONB) TO authenticated;

-- Complete campaign bootstrap, including immutable historical parcel snapshots.
ALTER TABLE public.campaigns ADD COLUMN IF NOT EXISTS snapshot_format INTEGER NOT NULL DEFAULT 0;
CREATE TABLE public.campaign_parcels (
    id UUID PRIMARY KEY, workspace_id UUID NOT NULL, campaign_id UUID NOT NULL, parcel_id UUID NOT NULL,
    farm_id_at_start UUID NOT NULL, farm_name_at_start TEXT NOT NULL, parcel_name_at_start TEXT NOT NULL,
    managed_area_m2_at_start DOUBLE PRECISION, cadastral_reference_at_start TEXT, geometry_geo_json_snapshot TEXT,
    UNIQUE(campaign_id,parcel_id),
    FOREIGN KEY(campaign_id,workspace_id) REFERENCES public.campaigns(id,workspace_id),
    FOREIGN KEY(parcel_id,workspace_id) REFERENCES public.parcels(id,workspace_id),
    FOREIGN KEY(farm_id_at_start,workspace_id) REFERENCES public.farms(id,workspace_id)
);
ALTER TABLE public.campaign_parcels ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.campaign_parcels FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.campaign_parcels TO authenticated;
CREATE POLICY campaign_parcels_own_read ON public.campaign_parcels FOR SELECT TO authenticated
USING (coalesce(auth.jwt()->>'is_anonymous','false')='false' AND EXISTS (
    SELECT 1 FROM public.campaigns c JOIN public.farms f ON f.id=c.farm_id AND f.workspace_id=c.workspace_id
    JOIN public.workspaces w ON w.id=c.workspace_id
    WHERE c.id=campaign_id AND c.workspace_id=campaign_parcels.workspace_id AND c.deleted_at IS NULL
    AND f.deleted_at IS NULL AND w.deleted_at IS NULL AND w.owner_user_id=auth.uid()
    AND (f.creator_user_id=auth.uid() OR f.owner_id=auth.uid())
));

CREATE FUNCTION magina_g2_private.create_campaign(p_operation_id UUID,p_campaign JSONB)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
    actor UUID:=auth.uid(); claims JSONB:=auth.jwt(); field TEXT; campaign UUID; workspace UUID; farm UUID;
    starts DATE; ends DATE; child JSONB; child_id UUID; parcel UUID; original_farm UUID; area DOUBLE PRECISION;
    seen_ids UUID[]:='{}'; seen_parcels UUID[]:='{}'; payload_hash TEXT;
    saved magina_g2_private.sync_operations%ROWTYPE;
BEGIN
    IF actor IS NULL OR claims IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR (claims->>'exp') !~ '^[0-9]+$' THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501';
    END IF;
    IF (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501';
    END IF;
    IF p_operation_id IS NULL OR p_campaign IS NULL OR jsonb_typeof(p_campaign)<>'object'
       OR (SELECT count(*) FROM jsonb_object_keys(p_campaign))<>9
       OR NOT p_campaign ?& ARRAY['id','workspaceId','farmId','name','startDate','endDate','status','notes','parcels']
       OR octet_length(p_campaign::text)>4194304 THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
    END IF;
    FOREACH field IN ARRAY ARRAY['id','workspaceId','farmId','name','startDate','status'] LOOP
        IF jsonb_typeof(p_campaign->field) IS DISTINCT FROM 'string' THEN
            RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
        END IF;
    END LOOP;
    IF jsonb_typeof(p_campaign->'endDate') NOT IN ('string','null')
       OR jsonb_typeof(p_campaign->'notes') NOT IN ('string','null')
       OR jsonb_typeof(p_campaign->'parcels') IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
    END IF;
    BEGIN
        campaign:=(p_campaign->>'id')::uuid; workspace:=(p_campaign->>'workspaceId')::uuid; farm:=(p_campaign->>'farmId')::uuid;
        IF p_campaign->>'startDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
           OR (p_campaign->>'endDate' IS NOT NULL AND p_campaign->>'endDate' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') THEN
            RAISE invalid_parameter_value;
        END IF;
        starts:=(p_campaign->>'startDate')::date; ends:=(p_campaign->>'endDate')::date;
    EXCEPTION WHEN invalid_text_representation OR datetime_field_overflow OR invalid_datetime_format OR invalid_parameter_value THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
    END;
    IF length(p_campaign->>'name') NOT BETWEEN 1 AND 200 OR btrim(p_campaign->>'name')=''
       OR ends<starts OR p_campaign->>'status' NOT IN ('PREPARATION','ACTIVE','HARVEST','CLOSED')
       OR length(p_campaign->>'notes')>10000 OR jsonb_array_length(p_campaign->'parcels')>1000
       OR (p_campaign->>'status'<>'PREPARATION' AND jsonb_array_length(p_campaign->'parcels')=0) THEN
        RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
    END IF;
    FOR child IN SELECT value FROM jsonb_array_elements(p_campaign->'parcels') LOOP
        IF jsonb_typeof(child)<>'object' OR (SELECT count(*) FROM jsonb_object_keys(child))<>8
           OR NOT child ?& ARRAY['id','parcelId','farmIdAtStart','farmNameAtStart','parcelNameAtStart',
               'managedAreaM2AtStart','cadastralReferenceAtStart','geometryGeoJsonSnapshot'] THEN
            RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
        END IF;
        FOREACH field IN ARRAY ARRAY['id','parcelId','farmIdAtStart','farmNameAtStart','parcelNameAtStart'] LOOP
            IF jsonb_typeof(child->field) IS DISTINCT FROM 'string' THEN
                RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
            END IF;
        END LOOP;
        IF jsonb_typeof(child->'managedAreaM2AtStart') NOT IN ('number','null')
           OR jsonb_typeof(child->'cadastralReferenceAtStart') NOT IN ('string','null')
           OR jsonb_typeof(child->'geometryGeoJsonSnapshot') NOT IN ('string','null') THEN
            RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
        END IF;
        BEGIN
            child_id:=(child->>'id')::uuid; parcel:=(child->>'parcelId')::uuid;
            original_farm:=(child->>'farmIdAtStart')::uuid; area:=(child->>'managedAreaM2AtStart')::double precision;
        EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
            RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
        END;
        IF original_farm<>farm OR child_id=ANY(seen_ids) OR parcel=ANY(seen_parcels) OR area<0
           OR length(child->>'farmNameAtStart') NOT BETWEEN 1 AND 200 OR btrim(child->>'farmNameAtStart')=''
           OR length(child->>'parcelNameAtStart') NOT BETWEEN 1 AND 200 OR btrim(child->>'parcelNameAtStart')=''
           OR length(child->>'cadastralReferenceAtStart')>200 THEN
            RAISE EXCEPTION 'campaign invalid' USING ERRCODE='22023';
        END IF;
        seen_ids:=array_append(seen_ids,child_id); seen_parcels:=array_append(seen_parcels,parcel);
    END LOOP;
    payload_hash:=encode(sha256(convert_to(p_campaign::text,'UTF8')),'hex');
    PERFORM pg_advisory_xact_lock(hashtextextended(actor::text||p_operation_id::text,0));
    PERFORM 1 FROM public.workspaces WHERE id=workspace AND owner_user_id=actor AND deleted_at IS NULL FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
    -- Serializes running-campaign checks between callers of this creation RPC.
    PERFORM 1 FROM public.farms WHERE id=farm AND workspace_id=workspace AND deleted_at IS NULL
        AND (creator_user_id=actor OR owner_id=actor) FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
    SELECT * INTO saved FROM magina_g2_private.sync_operations WHERE account_id=actor AND operation_id=p_operation_id;
    IF FOUND THEN
        IF saved.entity_type<>'CAMPAIGN' OR saved.entity_id<>campaign OR saved.payload_hash<>payload_hash THEN
            RAISE EXCEPTION 'campaign conflict' USING ERRCODE='23505';
        END IF;
        PERFORM 1 FROM public.campaigns WHERE id=campaign AND workspace_id=workspace AND farm_id=farm
            AND deleted_at IS NULL AND snapshot_format=1 FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
        RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',campaign,
            'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',true);
    END IF;
    IF p_campaign->>'status' IN ('ACTIVE','HARVEST') AND EXISTS (
        SELECT 1 FROM public.campaigns WHERE farm_id=farm AND deleted_at IS NULL AND status IN ('ACTIVE','HARVEST')) THEN
        RAISE EXCEPTION 'campaign conflict' USING ERRCODE='23505';
    END IF;
    FOREACH parcel IN ARRAY seen_parcels LOOP
        -- Historical import: require the association to exist, not to still be current today.
        PERFORM 1 FROM public.parcels p JOIN public.farm_parcel_memberships m ON m.parcel_id=p.id AND m.workspace_id=p.workspace_id
            WHERE p.id=parcel AND p.workspace_id=workspace AND m.farm_id=farm FOR SHARE OF p,m;
        IF NOT FOUND THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
    END LOOP;
    INSERT INTO public.campaigns(id,workspace_id,farm_id,name,start_date,end_date,status,notes,snapshot_format)
    VALUES(campaign,workspace,farm,p_campaign->>'name',starts,ends,p_campaign->>'status',p_campaign->>'notes',1)
    ON CONFLICT(id) DO NOTHING;
    IF NOT FOUND THEN RAISE EXCEPTION 'campaign conflict' USING ERRCODE='23505'; END IF;
    FOR child IN SELECT value FROM jsonb_array_elements(p_campaign->'parcels') LOOP
        INSERT INTO public.campaign_parcels(id,workspace_id,campaign_id,parcel_id,farm_id_at_start,farm_name_at_start,
            parcel_name_at_start,managed_area_m2_at_start,cadastral_reference_at_start,geometry_geo_json_snapshot)
        VALUES((child->>'id')::uuid,workspace,campaign,(child->>'parcelId')::uuid,farm,child->>'farmNameAtStart',
            child->>'parcelNameAtStart',(child->>'managedAreaM2AtStart')::double precision,
            child->>'cadastralReferenceAtStart',child->>'geometryGeoJsonSnapshot');
    END LOOP;
    INSERT INTO magina_g2_private.sync_operations(account_id,operation_id,entity_type,entity_id,payload_hash,result_version)
    VALUES(actor,p_operation_id,'CAMPAIGN',campaign,payload_hash,1) RETURNING * INTO saved;
    RETURN jsonb_build_object('success',true,'operation_id',p_operation_id,'entity_id',campaign,
        'status',saved.status,'remote_version',saved.result_version,'payload_version',saved.payload_version,'idempotent',false);
END $$;
REVOKE ALL ON FUNCTION magina_g2_private.create_campaign(UUID,JSONB) FROM PUBLIC,anon,authenticated;
CREATE FUNCTION public.create_own_campaign(p_operation_id UUID,p_campaign JSONB)
RETURNS JSONB LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
    SELECT magina_g2_private.create_campaign(p_operation_id,p_campaign);
$$;
REVOKE ALL ON FUNCTION public.create_own_campaign(UUID,JSONB) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.create_own_campaign(UUID,JSONB) TO authenticated;

CREATE FUNCTION public.get_own_campaign(p_campaign_id UUID) RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE actor UUID:=auth.uid(); claims JSONB:=auth.jwt(); result JSONB;
BEGIN
    IF actor IS NULL OR claims IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR claims->>'sub' IS DISTINCT FROM actor::text OR coalesce(claims->>'is_anonymous','false')<>'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR (claims->>'exp') !~ '^[0-9]+$' THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501';
    END IF;
    IF (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501';
    END IF;
    SELECT jsonb_build_object('payload_version',1,'campaign',to_jsonb(c),'parcels',coalesce((
        SELECT jsonb_agg(to_jsonb(cp) ORDER BY cp.parcel_id) FROM public.campaign_parcels cp WHERE cp.campaign_id=c.id
    ),'[]'::jsonb)) INTO result FROM public.campaigns c
    JOIN public.farms f ON f.id=c.farm_id AND f.workspace_id=c.workspace_id
    JOIN public.workspaces w ON w.id=c.workspace_id
    WHERE c.id=p_campaign_id AND c.snapshot_format=1 AND c.deleted_at IS NULL AND f.deleted_at IS NULL AND w.deleted_at IS NULL
      AND EXISTS(SELECT 1 FROM magina_g2_private.sync_operations WHERE account_id=actor AND entity_type='CAMPAIGN' AND entity_id=c.id)
      AND w.owner_user_id=actor AND (f.creator_user_id=actor OR f.owner_id=actor);
    IF result IS NULL THEN RAISE EXCEPTION 'campaign denied' USING ERRCODE='42501'; END IF;
    RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.get_own_campaign(UUID) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_campaign(UUID) TO authenticated;
