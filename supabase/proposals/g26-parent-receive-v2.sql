-- G2.6 2c-1: parents read contract v2 for the B receiver (plan G2.6-PARCEL-RECEIVE-2C, #810 D1/D2).
-- Proposal only: disposable Supabase. NEVER apply to DEV or production without a separate
-- deployment decision and backup/restore plan. get_own_workspace_parents_v1 stays unchanged.
--
-- Load after g2-parcel-bootstrap.sql (exact instant columns), 20261029044901_g26_workspace_authorization.sql
-- (verify_workspace_access_v1) and g26-parcel-mutation-v1.sql (parcels.deleted_at_exact, archive/restore).
--
-- Differences with v1:
--   * ARCHIVED parcels travel (an archive writes parcels.deleted_at), with their exact archivedAt.
--   * Every non-deleted membership of those parcels travels, open and closed: the full history.
--   * Farms: live ACTIVE ones and real archives (status ARCHIVED with deleted_at, as Android writes
--     them), with archivedAt. v1 hides archived farms and silently drops their periods.
--   * The graph is rejected whole when a parcel breaks the #805 invariant, an ARCHIVED farm has no
--     archive instant, or a membership points
--     to a farm outside the farms of the graph (deleted or foreign). Nothing is clipped or dropped.
--   * Limits: 100 farms, 1000 parcels, 5000 memberships and 8 MiB of JSON. Above them: 54000.
--
-- One STABLE function: every query sees the caller's single snapshot, so the graph is consistent.
CREATE FUNCTION public.get_own_workspace_parents_v2(p_workspace_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    claims jsonb := auth.jwt();
    actor uuid := auth.uid();
    access jsonb;
    w public.workspaces%ROWTYPE;
    farms jsonb;
    parcels jsonb;
    memberships jsonb;
    result jsonb;
BEGIN
    IF p_workspace_id IS NULL OR actor IS NULL OR claims IS NULL
       OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR claims->>'sub' IS DISTINCT FROM actor::text
       OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR coalesce(claims->>'is_anonymous','false') <> 'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
       OR claims->>'exp' !~ '^[0-9]{1,12}$'
       OR (claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'parents denied' USING ERRCODE = '42501';
    END IF;

    -- Same single authorization decision as v1 and the device grant cache: owner, or ACTIVE
    -- unrevoked membership with 'read' inside its validity window.
    access := public.verify_workspace_access_v1(p_workspace_id, 'read');
    IF (access->>'authorized')::boolean IS NOT TRUE
       OR access->>'subject' IS DISTINCT FROM actor::text THEN
        RAISE EXCEPTION 'parents denied' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO w FROM public.workspaces WHERE id = p_workspace_id AND deleted_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'parents denied' USING ERRCODE = '42501'; END IF;

    -- Farms of the graph (#812 audit): live ACTIVE farms and real archives. Android archives a farm
    -- with status ARCHIVED *and* deleted_at, and leaves its parcels' periods as they are (archive is
    -- not a cascade). A row with deleted_at and any other status is a deletion, never returned.
    -- Row limits first, so an oversized workspace never builds its JSON.
    IF (SELECT count(*) FROM public.farms f WHERE f.workspace_id = w.id
        AND ((f.status = 'ACTIVE' AND f.deleted_at IS NULL) OR (f.status = 'ARCHIVED' AND f.deleted_at IS NOT NULL))) > 100
       OR (SELECT count(*) FROM public.parcels p WHERE p.workspace_id = w.id) > 1000
       OR (SELECT count(*) FROM public.farm_parcel_memberships m
           JOIN public.parcels p ON p.id = m.parcel_id AND p.workspace_id = w.id
           WHERE m.deleted_at IS NULL) > 5000 THEN
        RAISE EXCEPTION 'parents too large for contract v2: rows' USING ERRCODE = '54000';
    END IF;

    IF EXISTS (SELECT 1 FROM public.farms f WHERE f.workspace_id = w.id AND f.deleted_at IS NULL
               AND f.status NOT IN ('ACTIVE', 'ARCHIVED'))
       OR EXISTS (SELECT 1 FROM public.parcels p WHERE p.workspace_id = w.id
               AND p.status NOT IN ('ACTIVE', 'ARCHIVED')) THEN
        RAISE EXCEPTION 'parents incomplete: unsupported status' USING ERRCODE = '22023';
    END IF;

    -- An ARCHIVED farm without its archive instant cannot be told apart from a live one on B.
    IF EXISTS (SELECT 1 FROM public.farms f WHERE f.workspace_id = w.id
               AND f.status = 'ARCHIVED' AND f.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'parents incomplete: farm invariant' USING ERRCODE = '22023';
    END IF;

    -- Every membership of a returned parcel belongs to this workspace and to one of the farms of the
    -- graph (live ACTIVE or a real archive). A deleted or foreign farm would leave a hole in the history.
    IF EXISTS (SELECT 1 FROM public.farm_parcel_memberships m
               JOIN public.parcels p ON p.id = m.parcel_id AND p.workspace_id = w.id
               WHERE m.deleted_at IS NULL AND (m.workspace_id IS DISTINCT FROM w.id OR NOT EXISTS (
                   SELECT 1 FROM public.farms f WHERE f.id = m.farm_id AND f.workspace_id = w.id
                   AND ((f.status = 'ACTIVE' AND f.deleted_at IS NULL)
                     OR (f.status = 'ARCHIVED' AND f.deleted_at IS NOT NULL)))))
       OR EXISTS (SELECT 1 FROM public.farm_parcel_memberships m
               WHERE m.workspace_id = w.id AND m.deleted_at IS NULL AND NOT EXISTS (
                   SELECT 1 FROM public.parcels p WHERE p.id = m.parcel_id AND p.workspace_id = w.id)) THEN
        RAISE EXCEPTION 'parents incomplete: membership farm' USING ERRCODE = '22023';
    END IF;

    -- The #805 invariant of every parcel: ACTIVE has no deleted_at and exactly one open period;
    -- ARCHIVED has deleted_at and none. Legacy soft-deleted ACTIVE rows also fail here.
    IF EXISTS (SELECT 1 FROM public.parcels p WHERE p.workspace_id = w.id AND NOT (
           (p.status = 'ACTIVE' AND p.deleted_at IS NULL AND (SELECT count(*) FROM public.farm_parcel_memberships m
               WHERE m.parcel_id = p.id AND m.deleted_at IS NULL AND m.valid_until IS NULL) = 1)
        OR (p.status = 'ARCHIVED' AND p.deleted_at IS NOT NULL AND (SELECT count(*) FROM public.farm_parcel_memberships m
               WHERE m.parcel_id = p.id AND m.deleted_at IS NULL AND m.valid_until IS NULL) = 0))) THEN
        RAISE EXCEPTION 'parents incomplete: parcel invariant' USING ERRCODE = '22023';
    END IF;

    SELECT coalesce(jsonb_agg(jsonb_build_object(
        'id', f.id, 'workspaceId', f.workspace_id, 'name', f.name,
        'description', f.description, 'municipality', f.municipality, 'province', f.province,
        'notes', f.notes, 'status', f.status,
        -- Real archive instant (timestamptz: microseconds, enough for Android's millisecond clock).
        'archivedAt', CASE WHEN f.status = 'ARCHIVED' THEN to_jsonb(f.deleted_at) ELSE 'null'::jsonb END,
        'createdAt', f.created_at, 'updatedAt', f.updated_at, 'version', f.version
    ) ORDER BY f.id), '[]'::jsonb) INTO farms
    FROM public.farms f WHERE f.workspace_id = w.id
        AND ((f.status = 'ACTIVE' AND f.deleted_at IS NULL) OR (f.status = 'ARCHIVED' AND f.deleted_at IS NOT NULL));

    SELECT coalesce(jsonb_agg(jsonb_build_object(
        'id', p.id, 'workspaceId', p.workspace_id, 'displayName', p.display_name,
        'cadastralReference', p.cadastral_reference, 'cadastralPolygon', p.cadastral_polygon,
        'cadastralParcel', p.cadastral_parcel, 'municipality', p.municipality,
        'province', p.province, 'source', p.source, 'geometryGeoJson', p.geometry_geo_json,
        'sourceProvider', p.source_provider,
        'sourceImportedAt', coalesce(to_jsonb(p.source_imported_at_exact), to_jsonb(p.source_imported_at)),
        'cadastralAreaM2', p.cadastral_area_m2, 'managedAreaM2', p.managed_area_m2,
        'notes', p.notes, 'status', p.status, 'oliveTreeCount', p.olive_tree_count,
        'variety', p.variety, 'irrigationSystem', p.irrigation_system,
        'irrigationNetwork', p.irrigation_network, 'irrigationSector', p.irrigation_sector,
        'irrigationDays', p.irrigation_days,
        -- The exact Android instant of the archive when present (PostgreSQL keeps microseconds).
        'archivedAt', CASE WHEN p.status = 'ARCHIVED'
            THEN coalesce(to_jsonb(p.deleted_at_exact), to_jsonb(p.deleted_at)) ELSE 'null'::jsonb END,
        'createdAt', p.created_at, 'updatedAt', p.updated_at, 'version', p.version
    ) ORDER BY p.id), '[]'::jsonb) INTO parcels
    FROM public.parcels p WHERE p.workspace_id = w.id;

    SELECT coalesce(jsonb_agg(jsonb_build_object(
        'id', m.id, 'workspaceId', m.workspace_id, 'farmId', m.farm_id, 'parcelId', m.parcel_id,
        'validFrom', coalesce(to_jsonb(m.valid_from_exact), to_jsonb(m.valid_from)),
        'validUntil', coalesce(to_jsonb(m.valid_until_exact), to_jsonb(m.valid_until)),
        'createdAt', m.created_at, 'updatedAt', m.updated_at, 'version', m.version
    ) ORDER BY m.id), '[]'::jsonb) INTO memberships
    FROM public.farm_parcel_memberships m
    JOIN public.parcels p ON p.id = m.parcel_id AND p.workspace_id = w.id
    WHERE m.deleted_at IS NULL;

    result := jsonb_build_object(
        'contract_version', 2,
        'issuer', claims->>'iss',
        'subject', actor,
        'workspace', jsonb_build_object(
            'id', w.id, 'name', w.name, 'ownerUserId', w.owner_user_id,
            'countryCode', w.country_code, 'timezone', w.timezone, 'locale', w.locale,
            'currency', w.currency, 'createdAt', w.created_at, 'updatedAt', w.updated_at,
            'version', w.version
        ),
        'farms', farms,
        'parcels', parcels,
        'memberships', memberships
    );
    -- One byte bound shared with the transport and the Android v2 parser (D2).
    IF octet_length(result::text) > 8388608 THEN
        RAISE EXCEPTION 'parents too large for contract v2: bytes' USING ERRCODE = '54000';
    END IF;
    RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION public.get_own_workspace_parents_v2(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_workspace_parents_v2(uuid) TO authenticated;
