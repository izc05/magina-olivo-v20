-- Isolated proposal for the B receiver of WORKSPACE -> FARM -> PARCEL parents.
-- Depends on 20261029044901_g26_workspace_authorization.sql and on the *_exact instant
-- columns of proposals/g2-parcel-bootstrap.sql (the Android value wins when present,
-- since PostgreSQL keeps only microseconds). NEVER apply to DEV or
-- production without a separate deployment decision and backup/restore plan.
--
-- One STABLE function runs every query against the caller's single MVCC snapshot, so
-- the workspace, farms, parcels and memberships are returned as one consistent graph.
-- Limits reject, never clip: a partial parent graph must not reach Room.
CREATE FUNCTION public.get_own_workspace_parents_v1(p_workspace_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    claims jsonb := auth.jwt();
    actor uuid := auth.uid();
    access jsonb;
    w public.workspaces%ROWTYPE;
    farms jsonb;
    parcels jsonb;
    memberships jsonb;
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

    -- The single authorization decision is the same one that fills the device grant cache:
    -- owner, or ACTIVE unrevoked membership with 'read' inside its validity window.
    access := public.verify_workspace_access_v1(p_workspace_id, 'read');
    IF (access->>'authorized')::boolean IS NOT TRUE
       OR access->>'subject' IS DISTINCT FROM actor::text THEN
        RAISE EXCEPTION 'parents denied' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO w FROM public.workspaces WHERE id = p_workspace_id AND deleted_at IS NULL;
    IF NOT FOUND THEN RAISE EXCEPTION 'parents denied' USING ERRCODE = '42501'; END IF;

    IF (SELECT count(*) FROM public.farms f WHERE f.workspace_id = w.id AND f.deleted_at IS NULL) > 100
       OR (SELECT count(*) FROM public.parcels p WHERE p.workspace_id = w.id AND p.deleted_at IS NULL) > 500
       OR (SELECT count(*) FROM public.farm_parcel_memberships m
           WHERE m.workspace_id = w.id AND m.deleted_at IS NULL) > 2000 THEN
        RAISE EXCEPTION 'parents too large for contract v1' USING ERRCODE = '54000';
    END IF;
    IF EXISTS (SELECT 1 FROM public.farms f WHERE f.workspace_id = w.id AND f.deleted_at IS NULL
               AND f.status NOT IN ('ACTIVE', 'ARCHIVED'))
       OR EXISTS (SELECT 1 FROM public.parcels p WHERE p.workspace_id = w.id AND p.deleted_at IS NULL
               AND p.status NOT IN ('ACTIVE', 'ARCHIVED')) THEN
        RAISE EXCEPTION 'parents incomplete: unsupported status' USING ERRCODE = '22023';
    END IF;

    SELECT coalesce(jsonb_agg(jsonb_build_object(
        'id', f.id, 'workspaceId', f.workspace_id, 'name', f.name,
        'description', f.description, 'municipality', f.municipality, 'province', f.province,
        'notes', f.notes, 'status', f.status,
        'createdAt', f.created_at, 'updatedAt', f.updated_at, 'version', f.version
    ) ORDER BY f.id), '[]'::jsonb) INTO farms
    FROM public.farms f WHERE f.workspace_id = w.id AND f.deleted_at IS NULL;

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
        'createdAt', p.created_at, 'updatedAt', p.updated_at, 'version', p.version
    ) ORDER BY p.id), '[]'::jsonb) INTO parcels
    FROM public.parcels p WHERE p.workspace_id = w.id AND p.deleted_at IS NULL;

    -- Only memberships whose farm and parcel are both live and in this workspace.
    SELECT coalesce(jsonb_agg(jsonb_build_object(
        'id', m.id, 'workspaceId', m.workspace_id, 'farmId', m.farm_id, 'parcelId', m.parcel_id,
        'validFrom', coalesce(to_jsonb(m.valid_from_exact), to_jsonb(m.valid_from)),
        'validUntil', coalesce(to_jsonb(m.valid_until_exact), to_jsonb(m.valid_until)),
        'createdAt', m.created_at, 'updatedAt', m.updated_at, 'version', m.version
    ) ORDER BY m.id), '[]'::jsonb) INTO memberships
    FROM public.farm_parcel_memberships m
    JOIN public.farms f ON f.id = m.farm_id AND f.workspace_id = w.id AND f.deleted_at IS NULL
    JOIN public.parcels p ON p.id = m.parcel_id AND p.workspace_id = w.id AND p.deleted_at IS NULL
    WHERE m.workspace_id = w.id AND m.deleted_at IS NULL;

    RETURN jsonb_build_object(
        'contract_version', 1,
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
END;
$$;
REVOKE ALL ON FUNCTION public.get_own_workspace_parents_v1(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_workspace_parents_v1(uuid) TO authenticated;
