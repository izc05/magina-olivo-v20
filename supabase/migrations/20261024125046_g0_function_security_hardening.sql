-- G0 DEV follow-up: close anonymous EXECUTE on privileged RPCs and pin trigger paths.
-- Keep authenticated EXECUTE: these RPCs check auth.uid() and farm capability internally.
-- Trigger functions run as invoker and remain available to their table triggers.

ALTER FUNCTION public.check_farm_principal_immutability()
  SET search_path = pg_catalog, public;
ALTER FUNCTION public.deny_delivery_hard_delete()
  SET search_path = pg_catalog, public;
ALTER FUNCTION public.check_contract_acceptance_integrity()
  SET search_path = pg_catalog, public;
ALTER FUNCTION public.check_share_agreement_acceptance_integrity()
  SET search_path = pg_catalog, public;

REVOKE EXECUTE ON FUNCTION public.accept_farm_contract(uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.accept_harvest_share_agreement(uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.claim_farm_invitation(text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.confirm_farm_invitation(uuid) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.has_farm_capability(uuid, text) FROM PUBLIC, anon;
REVOKE EXECUTE ON FUNCTION public.user_accessible_farm_ids() FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.accept_farm_contract(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.accept_harvest_share_agreement(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.claim_farm_invitation(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.confirm_farm_invitation(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.has_farm_capability(uuid, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.user_accessible_farm_ids() TO authenticated;
