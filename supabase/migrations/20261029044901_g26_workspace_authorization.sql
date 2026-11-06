-- G2.6 DEV proposal. Apply only to an ephemeral stack until separately approved for DEV.
-- The issuer is deliberately pinned to the DEV Auth project. A production rollout needs
-- a separately reviewed migration with its own issuer and backup evidence.

CREATE TABLE public.workspace_memberships (
    workspace_id uuid NOT NULL REFERENCES public.workspaces(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role text NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'MANAGER')),
    permissions text[] NOT NULL,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    valid_from timestamptz NOT NULL DEFAULT now(),
    valid_until timestamptz,
    revoked_at timestamptz,
    version bigint NOT NULL DEFAULT 1 CHECK (version > 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id),
    CHECK (array_length(permissions, 1) > 0
       AND permissions <@ ARRAY['read', 'write', 'manage_members']::text[]
       AND 'read' = ANY(permissions)),
    CHECK (valid_until IS NULL OR valid_until > valid_from),
    CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);

CREATE INDEX workspace_memberships_user_active_idx
    ON public.workspace_memberships(user_id, status, valid_until);

ALTER TABLE public.workspace_memberships ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.workspace_memberships FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.workspace_memberships TO authenticated;

CREATE POLICY workspace_memberships_read_own
    ON public.workspace_memberships FOR SELECT TO authenticated
    USING (user_id = (SELECT auth.uid()));

CREATE POLICY workspace_memberships_issuer_guard
    ON public.workspace_memberships AS RESTRICTIVE FOR SELECT TO authenticated
    USING ((SELECT auth.jwt()->>'iss') = 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1');

CREATE POLICY workspaces_select_active_member
    ON public.workspaces FOR SELECT TO authenticated
    USING (EXISTS (
        SELECT 1 FROM public.workspace_memberships m
        WHERE m.workspace_id = id AND m.user_id = (SELECT auth.uid())
          AND m.status = 'ACTIVE' AND m.revoked_at IS NULL
          AND m.valid_from <= now() AND (m.valid_until IS NULL OR m.valid_until > now())
          AND 'read' = ANY(m.permissions)
    ));

CREATE POLICY workspaces_issuer_guard
    ON public.workspaces AS RESTRICTIVE FOR ALL TO authenticated
    USING ((SELECT auth.jwt()->>'iss') = 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1')
    WITH CHECK ((SELECT auth.jwt()->>'iss') = 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1');

CREATE FUNCTION public.list_authorized_workspaces_v1()
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = '' AS $$
DECLARE
    claims jsonb := auth.jwt();
    actor uuid;
    token_exp timestamptz;
    checked_at timestamptz := now();
    result jsonb;
BEGIN
    IF claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
       OR claims->>'sub' IS NULL
       OR claims->>'sub' !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR claims->>'exp' IS NULL OR claims->>'exp' !~ '^[0-9]{1,12}$' THEN
        RAISE EXCEPTION 'invalid workspace identity' USING ERRCODE = '42501';
    END IF;
    actor := (claims->>'sub')::uuid;
    token_exp := to_timestamp((claims->>'exp')::bigint);
    IF token_exp <= checked_at OR actor IS DISTINCT FROM (SELECT auth.uid()) THEN
        RAISE EXCEPTION 'expired or invalid workspace identity' USING ERRCODE = '42501';
    END IF;

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
        'id', w.id,
        'name', w.name,
        'role', CASE WHEN w.owner_user_id = actor THEN 'OWNER' ELSE m.role END,
        'permissions', CASE WHEN w.owner_user_id = actor
            THEN to_jsonb(ARRAY['read','write','manage_members']::text[]) ELSE to_jsonb(m.permissions) END,
        'grant_version', CASE WHEN w.owner_user_id = actor THEN w.version ELSE m.version END,
        'authorization_expires_at', LEAST(token_exp, checked_at + interval '15 minutes',
            COALESCE(m.valid_until, 'infinity'::timestamptz))
    ) ORDER BY w.id), '[]'::jsonb) INTO result
    FROM public.workspaces w
    LEFT JOIN public.workspace_memberships m
      ON m.workspace_id = w.id AND m.user_id = actor
     AND m.status = 'ACTIVE' AND m.revoked_at IS NULL
     AND m.valid_from <= checked_at
     AND (m.valid_until IS NULL OR m.valid_until > checked_at)
     AND 'read' = ANY(m.permissions)
    WHERE w.deleted_at IS NULL
      AND (w.owner_user_id = actor OR m.user_id IS NOT NULL);

    RETURN jsonb_build_object(
        'contract_version', 1,
        'issuer', claims->>'iss',
        'subject', actor,
        'verified_at', checked_at,
        'token_expires_at', token_exp,
        'workspaces', result
    );
END;
$$;

REVOKE ALL ON FUNCTION public.list_authorized_workspaces_v1() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_authorized_workspaces_v1() TO authenticated;

CREATE FUNCTION public.verify_workspace_access_v1(p_workspace_id uuid, p_permission text DEFAULT 'read')
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = '' AS $$
DECLARE
    listing jsonb;
    item jsonb;
BEGIN
    IF p_workspace_id IS NULL OR p_permission NOT IN ('read', 'write', 'manage_members') THEN
        RAISE EXCEPTION 'invalid workspace query' USING ERRCODE = '22023';
    END IF;
    listing := public.list_authorized_workspaces_v1();
    SELECT value INTO item FROM jsonb_array_elements(listing->'workspaces') value
      WHERE value->>'id' = p_workspace_id::text
        AND value->'permissions' ? p_permission;
    RETURN jsonb_build_object(
        'contract_version', 1,
        'issuer', listing->>'issuer',
        'subject', listing->>'subject',
        'verified_at', listing->>'verified_at',
        'authorized', item IS NOT NULL,
        'workspace', item
    );
END;
$$;

REVOKE ALL ON FUNCTION public.verify_workspace_access_v1(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.verify_workspace_access_v1(uuid, text) TO authenticated;
