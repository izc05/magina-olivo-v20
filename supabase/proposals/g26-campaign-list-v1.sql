-- G26 manual sync: read-only list of the campaigns of one workspace, so a second device of the same account can find the
-- campaigns to receive. Same authorization as get_own_campaign_head_v1 (owner, or an ACTIVE member with 'read'); it never
-- writes. Only ids, farm and version travel: the content still comes from get_own_campaign_receive_v1.
-- Proposal only: not applied to DEV/PRODUCTION; used by the disposable local stack.
CREATE FUNCTION public.list_own_campaigns_v1(p_workspace_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    claims jsonb := auth.jwt();
    actor uuid := auth.uid();
    total bigint;
    result jsonb;
BEGIN
    IF p_workspace_id IS NULL OR actor IS NULL OR claims IS NULL
       OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR claims->>'sub' IS DISTINCT FROM actor::text
       OR claims->>'role' IS DISTINCT FROM 'authenticated'
       OR coalesce(claims->>'is_anonymous','false') <> 'false'
       OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
       OR claims->>'exp' IS NULL OR claims->>'exp' !~ '^[0-9]{1,12}$'
       OR (claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE = '42501';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.workspaces w
        WHERE w.id = p_workspace_id AND w.deleted_at IS NULL
          AND (w.owner_user_id = actor OR EXISTS (
              SELECT 1 FROM public.workspace_memberships m
              WHERE m.workspace_id = w.id AND m.user_id = actor AND m.status = 'ACTIVE'
                AND m.revoked_at IS NULL AND m.valid_from <= now()
                AND (m.valid_until IS NULL OR m.valid_until > now())
                AND 'read' = ANY(m.permissions)))) THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE = '42501';
    END IF;

    SELECT count(*) INTO total
    FROM public.campaigns c JOIN public.farms f ON f.id = c.farm_id AND f.workspace_id = c.workspace_id
    WHERE c.workspace_id = p_workspace_id AND c.deleted_at IS NULL AND c.snapshot_format = 1 AND f.deleted_at IS NULL;
    IF total > 1000 THEN
        RAISE EXCEPTION 'campaign list too large' USING ERRCODE = '54000';
    END IF;

    SELECT jsonb_build_object(
        'contract_version', 1, 'issuer', claims->>'iss', 'subject', actor, 'workspaceId', p_workspace_id,
        'campaigns', coalesce(jsonb_agg(jsonb_build_object('id', c.id, 'farmId', c.farm_id, 'version', c.version)
            ORDER BY c.id), '[]'::jsonb))
    INTO result
    FROM public.campaigns c JOIN public.farms f ON f.id = c.farm_id AND f.workspace_id = c.workspace_id
    WHERE c.workspace_id = p_workspace_id AND c.deleted_at IS NULL AND c.snapshot_format = 1 AND f.deleted_at IS NULL;
    RETURN result;
END;
$$;

REVOKE ALL ON FUNCTION public.list_own_campaigns_v1(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.list_own_campaigns_v1(uuid) TO authenticated;
