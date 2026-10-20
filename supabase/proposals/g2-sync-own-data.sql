-- Mágina Olivo — G2.1 Proposal: Idempotent Own-Data Sync & Farm Isolation
-- Candidate proposal only; not a migration or deployment artifact.
-- NO DEPLOY: Ephemeral native testing only. Never deploy to production or persistent DEV.

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

-- 3. Idempotent RPC: Sync Own Delivery (Pesada de aceituna)
CREATE OR REPLACE FUNCTION public.sync_own_delivery(p_operation_id UUID, p_delivery JSONB)
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
    v_payload_hash TEXT;
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
        IF v_saved.payload_hash <> v_payload_hash THEN
            RAISE EXCEPTION 'sync conflict: operation payload mismatch' USING ERRCODE = '23505';
        END IF;
        RETURN jsonb_build_object(
            'success', true,
            'operation_id', p_operation_id,
            'entity_id', v_saved.entity_id,
            'status', v_saved.status,
            'idempotent', true
        );
    END IF;

    -- Validate that the farm belongs to the actor (Own-data sync: must be owner/creator of farm)
    SELECT workspace_id INTO v_workspace
    FROM public.farms
    WHERE id = v_farm_id
      AND (
          creator_user_id = actor
          OR owner_id = actor
          OR (workspace_id IS NOT NULL AND workspace_id IN (
              SELECT id FROM public.workspaces WHERE owner_user_id = actor AND deleted_at IS NULL
          ))
      )
      AND deleted_at IS NULL;

    IF v_workspace IS NULL THEN
        RAISE EXCEPTION 'sync denied: actor does not own farm' USING ERRCODE = '42501';
    END IF;

    -- Validate campaign linkage
    PERFORM 1 FROM public.campaigns
    WHERE id = v_campaign_id AND farm_id = v_farm_id AND deleted_at IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'sync invalid: campaign does not belong to farm' USING ERRCODE = '22023';
    END IF;

    -- Apply delivery upsert
    SELECT * INTO v_existing_delivery FROM public.deliveries WHERE id = v_delivery_id;
    IF FOUND THEN
        IF v_existing_delivery.farm_id <> v_farm_id OR v_existing_delivery.workspace_id <> v_workspace THEN
            RAISE EXCEPTION 'sync conflict: delivery immutable binding mismatch' USING ERRCODE = '23505';
        END IF;

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
            version = version + 1,
            updated_at = now()
        WHERE id = v_delivery_id;
    ELSE
        INSERT INTO public.deliveries (
            id, workspace_id, farm_id, campaign_id, delivery_date, destination_name,
            net_grams, gross_grams, tare_grams, delivery_number, ticket_number,
            source, notes, harvest_id, delivery_time, harvest_origin,
            recorded_by_user_id, created_at, updated_at, version
        ) VALUES (
            v_delivery_id, v_workspace, v_farm_id, v_campaign_id, v_delivery_date, v_destination_name,
            v_net_grams, v_gross_grams, v_tare_grams, v_delivery_number, v_ticket_number,
            v_source, v_notes, v_harvest_id, v_delivery_time, v_harvest_origin,
            actor, now(), now(), 1
        );
    END IF;

    -- Record idempotent operation
    INSERT INTO magina_g2_private.sync_operations(
        account_id, operation_id, entity_type, entity_id, payload_hash, status
    ) VALUES (
        actor, p_operation_id, 'DELIVERY', v_delivery_id, v_payload_hash, 'APPLIED'
    );

    RETURN jsonb_build_object(
        'success', true,
        'operation_id', p_operation_id,
        'entity_id', v_delivery_id,
        'status', 'APPLIED',
        'idempotent', false
    );
END;
$$;

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
    v_payload_hash TEXT;
    v_saved magina_g2_private.sync_operations%ROWTYPE;
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
        IF v_saved.payload_hash <> v_payload_hash THEN
            RAISE EXCEPTION 'sync conflict: operation payload mismatch' USING ERRCODE = '23505';
        END IF;
        RETURN jsonb_build_object(
            'success', true,
            'operation_id', p_operation_id,
            'entity_id', v_saved.entity_id,
            'status', v_saved.status,
            'idempotent', true
        );
    END IF;

    -- Validate farm ownership (strictly owner/creator of farm)
    SELECT workspace_id INTO v_workspace
    FROM public.farms
    WHERE id = v_farm_id
      AND (
          creator_user_id = actor
          OR owner_id = actor
          OR (workspace_id IS NOT NULL AND workspace_id IN (
              SELECT id FROM public.workspaces WHERE owner_user_id = actor AND deleted_at IS NULL
          ))
      )
      AND deleted_at IS NULL;

    IF v_workspace IS NULL THEN
        RAISE EXCEPTION 'sync denied: actor does not own farm for economic operations' USING ERRCODE = '42501';
    END IF;

    -- Apply expense upsert
    IF EXISTS (SELECT 1 FROM public.expenses WHERE id = v_expense_id) THEN
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
        WHERE id = v_expense_id AND workspace_id = v_workspace;
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

    INSERT INTO magina_g2_private.sync_operations(
        account_id, operation_id, entity_type, entity_id, payload_hash, status
    ) VALUES (
        actor, p_operation_id, 'EXPENSE', v_expense_id, v_payload_hash, 'APPLIED'
    );

    RETURN jsonb_build_object(
        'success', true,
        'operation_id', p_operation_id,
        'entity_id', v_expense_id,
        'status', 'APPLIED',
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
BEGIN
    IF actor IS NULL THEN
        RAISE EXCEPTION 'access denied: unauthenticated' USING ERRCODE = '42501';
    END IF;

    SELECT coalesce(jsonb_agg(operation_id), '[]'::jsonb) INTO v_confirmed
    FROM magina_g2_private.sync_operations
    WHERE account_id = actor
      AND operation_id = ANY(p_operation_ids);

    RETURN jsonb_build_object(
        'success', true,
        'confirmed_operations', v_confirmed
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
