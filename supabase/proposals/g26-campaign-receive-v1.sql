-- Isolated proposal for the B receiver. Depends on g26-campaign-head-v1.sql.
-- NEVER apply to DEV or production without a separate deployment decision.
CREATE FUNCTION public.get_own_campaign_receive_v1(p_campaign_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    authorized_head jsonb;
    result jsonb;
BEGIN
    -- The existing HEAD is the single authorization decision: current validated JWT,
    -- owner or active read membership, and live workspace/farm/campaign.
    authorized_head := public.get_own_campaign_head_v1(p_campaign_id);
    SELECT jsonb_build_object(
        'contract_version', 1,
        'issuer', authorized_head->>'issuer',
        'subject', authorized_head->>'subject',
        'campaign', jsonb_build_object(
            'id', c.id, 'workspaceId', c.workspace_id, 'farmId', c.farm_id,
            'name', c.name, 'startDate', c.start_date, 'endDate', c.end_date,
            'status', c.status, 'notes', c.notes, 'version', c.version,
            'createdAt', c.created_at, 'updatedAt', c.updated_at,
            'snapshotFormat', c.snapshot_format
        ),
        'snapshots', coalesce((
            SELECT jsonb_agg(jsonb_build_object(
                'id', cp.id, 'workspaceId', cp.workspace_id,
                'campaignId', cp.campaign_id, 'parcelId', cp.parcel_id,
                'farmIdAtStart', cp.farm_id_at_start,
                'farmNameAtStart', cp.farm_name_at_start,
                'parcelNameAtStart', cp.parcel_name_at_start,
                'managedAreaM2AtStart', cp.managed_area_m2_at_start,
                'cadastralReferenceAtStart', cp.cadastral_reference_at_start,
                'geometryGeoJsonSnapshot', cp.geometry_geo_json_snapshot
            ) ORDER BY cp.id)
            FROM public.campaign_parcels cp
            WHERE cp.campaign_id = c.id AND cp.workspace_id = c.workspace_id
        ), '[]'::jsonb)
    ) INTO result
    FROM public.campaigns c
    WHERE c.id = p_campaign_id AND c.deleted_at IS NULL
      AND c.snapshot_format = 1
      AND c.version = (authorized_head->>'version')::bigint
      AND c.workspace_id::text = authorized_head->'campaign'->>'workspaceId'
      AND c.farm_id::text = authorized_head->'campaign'->>'farmId';
    IF result IS NULL THEN
        RAISE EXCEPTION 'campaign denied' USING ERRCODE = '42501';
    END IF;
    RETURN result;
END;
$$;
REVOKE ALL ON FUNCTION public.get_own_campaign_receive_v1(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_own_campaign_receive_v1(uuid) TO authenticated;
