-- DEV candidate only; not a migration or deployment artifact. A different Supabase
-- project needs its own reviewed issuer literal before this proposal can be reused.
-- NO DEPLOY: native JWT/PostgREST and concurrent PostgreSQL validation pending.
CREATE SCHEMA magina_g1c_private;
REVOKE ALL ON SCHEMA magina_g1c_private FROM PUBLIC,anon,authenticated;
CREATE TABLE magina_g1c_private.g1c_reservations (
 actor_id uuid NOT NULL REFERENCES auth.users(id), operation_id uuid NOT NULL,
 farm_id uuid NOT NULL UNIQUE, payload_hash text NOT NULL,
 remote_workspace_id uuid NOT NULL REFERENCES public.workspaces(id),
 issuer text NOT NULL, receipt_id uuid NOT NULL DEFAULT gen_random_uuid(),
 PRIMARY KEY(actor_id,operation_id)
);
ALTER TABLE magina_g1c_private.g1c_reservations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON magina_g1c_private.g1c_reservations FROM PUBLIC,anon,authenticated;

CREATE FUNCTION magina_g1c_private.g1c_immutable() RETURNS trigger
LANGUAGE plpgsql SET search_path='' AS $$ BEGIN
 RAISE EXCEPTION 'reservation receipt immutable';
END $$;
REVOKE ALL ON FUNCTION magina_g1c_private.g1c_immutable() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER g1c_immutable BEFORE UPDATE OR DELETE ON magina_g1c_private.g1c_reservations
FOR EACH ROW EXECUTE FUNCTION magina_g1c_private.g1c_immutable();

CREATE FUNCTION magina_g1c_private.reserve_local_farm_g1c(p_request jsonb,p_remote_workspace uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
 actor uuid:=auth.uid(); op uuid; farm uuid; local_ws uuid; consent uuid;
 consent_version integer; aggregate_version bigint; manifest text;
 field text; bytes bytea:=''::bytea; payload_hash text; token_issuer text; claims jsonb;
 saved magina_g1c_private.g1c_reservations%ROWTYPE;
BEGIN
 IF actor IS NULL THEN RAISE EXCEPTION 'reservation denied' USING ERRCODE='42501'; END IF;
 -- The gateway verifies this JWT. SQL fixture claims do not prove signatures.
 claims:=current_setting('request.jwt.claims',true)::jsonb;
 IF claims->>'role' IS DISTINCT FROM 'authenticated'
 OR claims->>'sub' IS DISTINCT FROM actor::text
 OR coalesce(claims->>'is_anonymous','false')<>'false'
 OR jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number'
 OR (claims->>'exp') !~ '^[0-9]+$' THEN
  RAISE EXCEPTION 'reservation denied' USING ERRCODE='42501'; END IF;
 IF (claims->>'exp')::numeric<=extract(epoch FROM now()) THEN
  RAISE EXCEPTION 'reservation denied' USING ERRCODE='42501'; END IF;
 token_issuer:=claims->>'iss';
 IF token_issuer IS DISTINCT FROM 'https://zubpicdibqbmewxeurhm.supabase.co/auth/v1' THEN
   RAISE EXCEPTION 'reservation denied' USING ERRCODE='42501'; END IF;
 token_issuer:='https://zubpicdibqbmewxeurhm.supabase.co';
 IF p_request IS NULL OR jsonb_typeof(p_request)<>'object'
 OR (SELECT count(*) FROM jsonb_object_keys(p_request))<>8
 OR NOT p_request ?& ARRAY['payloadVersion','operationId','farmId','localWorkspaceId','consentId','consentTextVersion','manifestSha256','aggregateVersion']
 THEN
   RAISE EXCEPTION 'reservation invalid' USING ERRCODE='22023';
 END IF;
 FOREACH field IN ARRAY ARRAY['operationId','farmId','localWorkspaceId','consentId','manifestSha256'] LOOP
  IF jsonb_typeof(p_request->field) IS DISTINCT FROM 'string' THEN
   RAISE EXCEPTION 'reservation invalid' USING ERRCODE='22023'; END IF;
 END LOOP;
 FOREACH field IN ARRAY ARRAY['payloadVersion','consentTextVersion','aggregateVersion'] LOOP
  IF jsonb_typeof(p_request->field) IS DISTINCT FROM 'number' THEN
   RAISE EXCEPTION 'reservation invalid' USING ERRCODE='22023'; END IF;
 END LOOP;
 BEGIN
  IF (p_request->>'payloadVersion') IS DISTINCT FROM '1' THEN RAISE invalid_parameter_value; END IF;
  op:=(p_request->>'operationId')::uuid; farm:=(p_request->>'farmId')::uuid;
  local_ws:=(p_request->>'localWorkspaceId')::uuid; consent:=(p_request->>'consentId')::uuid;
  consent_version:=(p_request->>'consentTextVersion')::integer;
  aggregate_version:=(p_request->>'aggregateVersion')::bigint;
  manifest:=p_request->>'manifestSha256';
  IF op IS NULL OR farm IS NULL OR local_ws IS NULL OR consent IS NULL OR consent_version IS NULL
  OR aggregate_version IS NULL OR manifest IS NULL OR consent_version<1 OR aggregate_version<1
  OR manifest !~ '^[0-9a-f]{64}$' THEN RAISE invalid_parameter_value; END IF;
 EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range OR invalid_parameter_value THEN
  RAISE EXCEPTION 'reservation invalid' USING ERRCODE='22023';
 END;
 -- Workspace reference from Room is not remote authority. Only live workspace owner qualifies.
 PERFORM 1 FROM public.workspaces WHERE id=p_remote_workspace AND owner_user_id=actor AND deleted_at IS NULL FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'reservation denied' USING ERRCODE='42501'; END IF;
 FOREACH field IN ARRAY ARRAY['magina-reservation','1',op::text,farm::text,local_ws::text,consent::text,consent_version::text,manifest,aggregate_version::text] LOOP
  bytes:=bytes||int4send(octet_length(convert_to(field,'UTF8')))||convert_to(field,'UTF8');
 END LOOP;
 payload_hash:=encode(sha256(bytes),'hex');
 -- Same lock in the farm INSERT trigger closes reservation/creation check-then-write race.
 PERFORM pg_advisory_xact_lock(hashtextextended(farm::text,0));
 SELECT * INTO saved FROM magina_g1c_private.g1c_reservations WHERE actor_id=actor AND operation_id=op;
 IF FOUND THEN
  IF saved.payload_hash<>payload_hash OR saved.remote_workspace_id<>p_remote_workspace OR saved.issuer<>token_issuer THEN
   RAISE EXCEPTION 'reservation conflict' USING ERRCODE='23505'; END IF;
 ELSE
  IF EXISTS(SELECT 1 FROM public.farms WHERE id=farm) THEN RAISE EXCEPTION 'reservation conflict' USING ERRCODE='23505'; END IF;
  BEGIN
   INSERT INTO magina_g1c_private.g1c_reservations(actor_id,operation_id,farm_id,payload_hash,remote_workspace_id,issuer)
   VALUES(actor,op,farm,payload_hash,p_remote_workspace,token_issuer) RETURNING * INTO saved;
  EXCEPTION WHEN unique_violation THEN
   -- Includes concurrent same-operation/different-farm requests; no foreign metadata leaks.
   SELECT * INTO saved FROM magina_g1c_private.g1c_reservations WHERE actor_id=actor AND operation_id=op;
   IF NOT FOUND OR saved.payload_hash<>payload_hash OR saved.remote_workspace_id<>p_remote_workspace OR saved.issuer<>token_issuer THEN
    RAISE EXCEPTION 'reservation conflict' USING ERRCODE='23505'; END IF;
  END;
 END IF;
 RETURN jsonb_build_object('issuer',saved.issuer,'actorId',saved.actor_id,'operationId',saved.operation_id,
 'farmId',saved.farm_id,'payloadHash',saved.payload_hash,'remoteWorkspaceId',saved.remote_workspace_id,'receiptId',saved.receipt_id);
END $$;
REVOKE ALL ON FUNCTION magina_g1c_private.reserve_local_farm_g1c(jsonb,uuid) FROM PUBLIC,anon,authenticated;
GRANT USAGE ON SCHEMA magina_g1c_private TO authenticated;
GRANT EXECUTE ON FUNCTION magina_g1c_private.reserve_local_farm_g1c(jsonb,uuid) TO authenticated;
CREATE FUNCTION public.reserve_local_farm_g1c(p_request jsonb,p_remote_workspace uuid)
RETURNS jsonb LANGUAGE sql SECURITY INVOKER SET search_path='' AS $$
 SELECT magina_g1c_private.reserve_local_farm_g1c(p_request,p_remote_workspace);
$$;
REVOKE ALL ON FUNCTION public.reserve_local_farm_g1c(jsonb,uuid) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.reserve_local_farm_g1c(jsonb,uuid) TO authenticated;

CREATE FUNCTION magina_g1c_private.g1c_guard_reserved_farm() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE saved magina_g1c_private.g1c_reservations%ROWTYPE;
BEGIN
 PERFORM pg_advisory_xact_lock(hashtextextended(NEW.id::text,0));
 SELECT * INTO saved FROM magina_g1c_private.g1c_reservations WHERE farm_id=NEW.id;
 IF FOUND AND (auth.uid() IS DISTINCT FROM saved.actor_id OR NEW.workspace_id IS DISTINCT FROM saved.remote_workspace_id
 OR NEW.creator_user_id IS DISTINCT FROM saved.actor_id OR (NEW.owner_id IS NOT NULL AND NEW.owner_id<>saved.actor_id)) THEN
  RAISE EXCEPTION 'reservation conflict' USING ERRCODE='23505'; END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION magina_g1c_private.g1c_guard_reserved_farm() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER g1c_guard_reserved_farm BEFORE INSERT ON public.farms
FOR EACH ROW EXECUTE FUNCTION magina_g1c_private.g1c_guard_reserved_farm();
