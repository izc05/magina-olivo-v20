-- G2.5 proposal. Apply only after G0, G1c and G2.1; never backfill unknown origin.
-- A NULL origin field means this row cannot be imported into Room.
ALTER TABLE public.campaign_parcels
  ADD COLUMN origin_created_at timestamptz,
  ADD COLUMN origin_updated_at timestamptz,
  ADD COLUMN origin_version bigint,
  ADD COLUMN origin_evidence_sha256 text;
ALTER TABLE public.delivery_parcels
  ADD COLUMN origin_created_at timestamptz,
  ADD COLUMN origin_updated_at timestamptz,
  ADD COLUMN origin_version bigint,
  ADD COLUMN origin_evidence_sha256 text;
ALTER TABLE public.campaign_parcels
  ADD CONSTRAINT campaign_parcels_origin_valid CHECK
    (origin_version IS NULL OR origin_version > 0),
  ADD CONSTRAINT campaign_parcels_origin_order CHECK
    (origin_created_at IS NULL OR origin_updated_at IS NULL OR origin_created_at <= origin_updated_at),
  ADD CONSTRAINT campaign_parcels_origin_complete CHECK
    ((origin_created_at IS NULL AND origin_updated_at IS NULL AND origin_version IS NULL AND origin_evidence_sha256 IS NULL)
      OR (origin_created_at IS NOT NULL AND origin_updated_at IS NOT NULL AND origin_version IS NOT NULL
        AND origin_evidence_sha256 ~ '^[0-9a-f]{64}$'));
ALTER TABLE public.delivery_parcels
  ADD CONSTRAINT delivery_parcels_origin_valid CHECK
    (origin_version IS NULL OR origin_version > 0),
  ADD CONSTRAINT delivery_parcels_origin_order CHECK
    (origin_created_at IS NULL OR origin_updated_at IS NULL OR origin_created_at <= origin_updated_at),
  ADD CONSTRAINT delivery_parcels_origin_complete CHECK
    ((origin_created_at IS NULL AND origin_updated_at IS NULL AND origin_version IS NULL AND origin_evidence_sha256 IS NULL)
      OR (origin_created_at IS NOT NULL AND origin_updated_at IS NOT NULL AND origin_version IS NOT NULL
        AND origin_evidence_sha256 ~ '^[0-9a-f]{64}$'));

ALTER TABLE public.harvests
  ADD CONSTRAINT harvests_receive_id_workspace_unique UNIQUE (id, workspace_id);
ALTER TABLE public.campaign_parcels
  ADD CONSTRAINT campaign_parcels_receive_id_workspace_unique UNIQUE (id, workspace_id);

CREATE TABLE public.harvest_parcels (
  id uuid PRIMARY KEY,
  workspace_id uuid NOT NULL,
  harvest_id uuid NOT NULL,
  parcel_id uuid NOT NULL,
  campaign_parcel_id uuid,
  parcel_name_at_harvest text NOT NULL,
  weight_grams bigint,
  allocation_mode text NOT NULL,
  origin_created_at timestamptz,
  origin_updated_at timestamptz,
  origin_version bigint,
  origin_evidence_sha256 text,
  UNIQUE (harvest_id, parcel_id),
  FOREIGN KEY (workspace_id) REFERENCES public.workspaces(id),
  FOREIGN KEY (harvest_id, workspace_id) REFERENCES public.harvests(id, workspace_id),
  FOREIGN KEY (parcel_id, workspace_id) REFERENCES public.parcels(id, workspace_id),
  FOREIGN KEY (campaign_parcel_id, workspace_id) REFERENCES public.campaign_parcels(id, workspace_id),
  CONSTRAINT harvest_parcels_allocation_valid CHECK
    ((allocation_mode = 'EXACT' AND weight_grams > 0)
      OR (allocation_mode = 'UNALLOCATED' AND weight_grams IS NULL)),
  CONSTRAINT harvest_parcels_origin_valid CHECK
    (origin_version IS NULL OR origin_version > 0),
  CONSTRAINT harvest_parcels_origin_order CHECK
    (origin_created_at IS NULL OR origin_updated_at IS NULL OR origin_created_at <= origin_updated_at),
  CONSTRAINT harvest_parcels_origin_complete CHECK
    ((origin_created_at IS NULL AND origin_updated_at IS NULL AND origin_version IS NULL AND origin_evidence_sha256 IS NULL)
      OR (origin_created_at IS NOT NULL AND origin_updated_at IS NOT NULL AND origin_version IS NOT NULL
        AND origin_evidence_sha256 ~ '^[0-9a-f]{64}$'))
);
CREATE INDEX harvest_parcels_workspace_idx ON public.harvest_parcels(workspace_id);
CREATE INDEX harvest_parcels_harvest_idx ON public.harvest_parcels(harvest_id);
ALTER TABLE public.harvest_parcels ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.harvest_parcels FROM PUBLIC, anon, authenticated;
-- No direct Data API access. RLS defaults to deny; only the validated RPC reads it.

-- One STABLE PostgreSQL statement sees one MVCC snapshot. This is deliberately
-- limited to the one-parcel G2.5 proof; multi-parcel data is rejected, not clipped.
CREATE FUNCTION public.get_own_delivery_receive_v1(p_delivery_id uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  actor uuid := auth.uid();
  claims jsonb := auth.jwt();
  d public.deliveries%ROWTYPE;
  h public.harvests%ROWTYPE;
  c public.campaigns%ROWTYPE;
  f public.farms%ROWTYPE;
  w public.workspaces%ROWTYPE;
  p public.parcels%ROWTYPE;
  m public.farm_parcel_memberships%ROWTYPE;
  cp public.campaign_parcels%ROWTYPE;
  hp public.harvest_parcels%ROWTYPE;
  dp public.delivery_parcels%ROWTYPE;
  head_hash text;
  n bigint;
BEGIN
  IF actor IS NULL OR claims IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated'
    OR claims->>'sub' IS DISTINCT FROM actor::text
    OR claims->>'iss' IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1'
    OR coalesce(claims->>'is_anonymous','false') <> 'false'
    OR coalesce(claims->>'exp','') !~ '^[0-9]+$' THEN
    RAISE EXCEPTION 'receive denied: identity' USING ERRCODE = '42501';
  END IF;
  IF (claims->>'exp')::numeric <= extract(epoch FROM now()) THEN
    RAISE EXCEPTION 'receive denied: expired' USING ERRCODE = '42501';
  END IF;

  SELECT * INTO d FROM public.deliveries WHERE id = p_delivery_id AND deleted_at IS NULL;
  IF NOT FOUND THEN RAISE EXCEPTION 'receive denied: unavailable' USING ERRCODE = '42501'; END IF;
  SELECT * INTO w FROM public.workspaces WHERE id = d.workspace_id AND owner_user_id = actor AND deleted_at IS NULL;
  IF NOT FOUND THEN RAISE EXCEPTION 'receive denied: unavailable' USING ERRCODE = '42501'; END IF;
  SELECT * INTO f FROM public.farms WHERE id = d.farm_id AND workspace_id = w.id AND deleted_at IS NULL
    AND (creator_user_id = actor OR owner_id = actor);
  IF NOT FOUND OR NOT public.has_farm_capability(d.farm_id, 'view_deliveries') THEN
    RAISE EXCEPTION 'receive denied: unavailable' USING ERRCODE = '42501';
  END IF;
  SELECT * INTO c FROM public.campaigns WHERE id = d.campaign_id AND farm_id = f.id
    AND workspace_id = w.id AND deleted_at IS NULL;
  IF NOT FOUND OR c.snapshot_format <> 1 OR d.snapshot_format <> 2 THEN
    RAISE EXCEPTION 'receive incomplete: campaign' USING ERRCODE = '22023';
  END IF;
  IF d.harvest_id IS NULL THEN RAISE EXCEPTION 'receive incomplete: harvest' USING ERRCODE = '22023'; END IF;
  SELECT * INTO h FROM public.harvests WHERE id = d.harvest_id AND farm_id = f.id
    AND campaign_id = c.id AND workspace_id = w.id AND deleted_at IS NULL;
  IF NOT FOUND THEN RAISE EXCEPTION 'receive incomplete: harvest' USING ERRCODE = '22023'; END IF;

  SELECT count(*) INTO n FROM public.delivery_parcels WHERE delivery_id = d.id;
  IF n <> 1 THEN RAISE EXCEPTION 'receive incomplete: delivery allocation' USING ERRCODE = '22023'; END IF;
  SELECT * INTO dp FROM public.delivery_parcels WHERE delivery_id = d.id;
  SELECT count(*) INTO n FROM public.harvest_parcels WHERE harvest_id = h.id;
  IF n <> 1 THEN RAISE EXCEPTION 'receive incomplete: harvest allocation' USING ERRCODE = '22023'; END IF;
  SELECT * INTO hp FROM public.harvest_parcels WHERE harvest_id = h.id;
  IF dp.workspace_id <> w.id OR hp.workspace_id <> w.id OR dp.parcel_id <> hp.parcel_id
    OR dp.campaign_parcel_id IS NULL OR hp.campaign_parcel_id IS NULL
    OR dp.campaign_parcel_id <> hp.campaign_parcel_id THEN
    RAISE EXCEPTION 'receive incomplete: allocation relation' USING ERRCODE = '22023';
  END IF;
  SELECT * INTO p FROM public.parcels WHERE id = dp.parcel_id AND workspace_id = w.id AND deleted_at IS NULL;
  IF NOT FOUND THEN RAISE EXCEPTION 'receive incomplete: parcel' USING ERRCODE = '22023'; END IF;
  SELECT * INTO cp FROM public.campaign_parcels WHERE id = dp.campaign_parcel_id
    AND campaign_id = c.id AND parcel_id = p.id AND workspace_id = w.id;
  IF NOT FOUND THEN RAISE EXCEPTION 'receive incomplete: campaign snapshot' USING ERRCODE = '22023'; END IF;
  SELECT count(*) INTO n FROM public.campaign_parcels WHERE campaign_id = c.id;
  IF n <> 1 THEN RAISE EXCEPTION 'receive incomplete: campaign snapshot count' USING ERRCODE = '22023'; END IF;
  SELECT count(*) INTO n FROM public.farm_parcel_memberships WHERE farm_id = f.id AND parcel_id = p.id
    AND workspace_id = w.id AND deleted_at IS NULL AND valid_until IS NULL;
  IF n <> 1 THEN RAISE EXCEPTION 'receive incomplete: membership' USING ERRCODE = '22023'; END IF;
  SELECT * INTO m FROM public.farm_parcel_memberships WHERE farm_id = f.id AND parcel_id = p.id
    AND workspace_id = w.id AND deleted_at IS NULL AND valid_until IS NULL;

  IF cp.origin_created_at IS NULL OR cp.origin_updated_at IS NULL OR cp.origin_version IS NULL OR cp.origin_evidence_sha256 IS NULL
    OR hp.origin_created_at IS NULL OR hp.origin_updated_at IS NULL OR hp.origin_version IS NULL OR hp.origin_evidence_sha256 IS NULL
    OR dp.origin_created_at IS NULL OR dp.origin_updated_at IS NULL OR dp.origin_version IS NULL OR dp.origin_evidence_sha256 IS NULL THEN
    RAISE EXCEPTION 'receive incomplete: unverified origin' USING ERRCODE = '22023';
  END IF;
  SELECT count(*), min(payload_hash) INTO n, head_hash FROM magina_g2_private.sync_operations
    WHERE account_id = actor AND entity_type = 'DELIVERY' AND entity_id = d.id
      AND result_version = d.version AND payload_version = 2 AND status = 'APPLIED';
  IF n <> 1 OR head_hash IS NULL THEN
    RAISE EXCEPTION 'receive incomplete: head changed' USING ERRCODE = '22023';
  END IF;
  RETURN jsonb_build_object(
    'receive_contract_version', 1, 'issuer', claims->>'iss', 'account_id', actor,
    'head_payload_hash', head_hash, 'head_version', d.version,
    'workspace', to_jsonb(w), 'farm', to_jsonb(f), 'parcel', to_jsonb(p),
    'membership', to_jsonb(m), 'campaign', to_jsonb(c),
    'campaign_parcel', to_jsonb(cp), 'harvest', to_jsonb(h),
    'harvest_parcel', to_jsonb(hp), 'delivery', to_jsonb(d),
    'delivery_parcel', to_jsonb(dp));
END; $$;
REVOKE ALL ON FUNCTION public.get_own_delivery_receive_v1(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_own_delivery_receive_v1(uuid) TO authenticated;
