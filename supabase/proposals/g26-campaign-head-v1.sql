-- Isolated proposal only. Requires G2.6 workspace memberships and G2.6 CAMPAIGN CAS.
-- Never apply to DEV or production as part of this Android PR.
CREATE FUNCTION public.get_own_campaign_head_v1(p_campaign_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    claims jsonb := auth.jwt();
    actor uuid := auth.uid();
    result jsonb;
BEGIN
    IF p_campaign_id IS NULL OR actor IS NULL OR claims IS NULL
       OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR claims->>'sub' IS DISTINCT FROM actor::text
       OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR coalesce(claims->>'is_anonymous','false') <> 'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
       OR claims->>'exp' IS NULL OR claims->>'exp' !~ '^[0-9]{1,12}$'
       OR (claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE = '42501';
    END IF;

    SELECT jsonb_build_object(
        'contract_version', 1, 'issuer', claims->>'iss', 'subject', actor,
        'version', c.version,
        'campaign', jsonb_build_object(
            'id', c.id, 'workspaceId', c.workspace_id, 'farmId', c.farm_id,
            'name', c.name, 'startDate', c.start_date, 'endDate', c.end_date,
            'status', c.status, 'notes', c.notes
        )
    ) INTO result
    FROM public.campaigns c
    JOIN public.farms f ON f.id = c.farm_id AND f.workspace_id = c.workspace_id
    JOIN public.workspaces w ON w.id = c.workspace_id
    WHERE c.id = p_campaign_id AND c.deleted_at IS NULL AND c.snapshot_format = 1
      AND f.deleted_at IS NULL AND w.deleted_at IS NULL
      AND (w.owner_user_id = actor OR EXISTS (
          SELECT 1 FROM public.workspace_memberships m
          WHERE m.workspace_id = w.id AND m.user_id = actor AND m.status = 'ACTIVE'
            AND m.revoked_at IS NULL AND m.valid_from <= now()
            AND (m.valid_until IS NULL OR m.valid_until > now())
            AND 'read' = ANY(m.permissions)
      ));
    IF result IS NULL THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE = '42501';
    END IF;
    RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION public.get_own_campaign_head_v1(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_campaign_head_v1(uuid) TO authenticated;
