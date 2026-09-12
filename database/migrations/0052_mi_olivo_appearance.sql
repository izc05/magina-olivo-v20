BEGIN;

CREATE TABLE mi_olivo_appearance (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  selected_badge TEXT NOT NULL DEFAULT 'none'
    CHECK (selected_badge IN ('none', 'roots', 'harvest', 'explorer', 'campaign')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION validate_mi_olivo_v5_campaign_reward()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  reward_campaign_id UUID;
  delivery_count INTEGER := 0;
  delivered_kg DOUBLE PRECISION := 0;
  confirmed_yield_exists BOOLEAN := false;
BEGIN
  IF NEW.rule_version <> 'mi-olivo-v5' OR NEW.points <= 0 THEN
    RETURN NEW;
  END IF;

  IF NEW.source_type <> 'campaign'
     OR NEW.source_id IS NULL
     OR NEW.workspace_id IS NULL
     OR NEW.source_id !~ '^[0-9a-fA-F-]{36}$' THEN
    RETURN NULL;
  END IF;

  BEGIN
    reward_campaign_id := NEW.source_id::uuid;
  EXCEPTION WHEN invalid_text_representation THEN
    RETURN NULL;
  END;

  IF NOT EXISTS (
    SELECT 1
    FROM campaigns c
    WHERE c.id = reward_campaign_id
      AND c.workspace_id = NEW.workspace_id
  ) THEN
    RETURN NULL;
  END IF;

  SELECT
    COUNT(*)::int,
    COALESCE(SUM(hd.total_kg), 0)::double precision
  INTO delivery_count, delivered_kg
  FROM harvest_deliveries hd
  WHERE hd.workspace_id = NEW.workspace_id
    AND hd.campaign_id = reward_campaign_id
    AND hd.created_by = NEW.user_id;

  SELECT EXISTS (
    SELECT 1
    FROM delivery_results dr
    JOIN harvest_deliveries hd ON hd.id = dr.delivery_id
    WHERE hd.workspace_id = NEW.workspace_id
      AND hd.campaign_id = reward_campaign_id
      AND hd.created_by = NEW.user_id
      AND dr.status = 'confirmed'
  ) INTO confirmed_yield_exists;

  IF NEW.event_type = 'campaign_three_deliveries' THEN
    IF NEW.points <> 15 OR delivery_count < 3 THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  IF NEW.event_type = 'campaign_1000kg' THEN
    IF NEW.points <> 20 OR delivered_kg < 1000 THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  IF NEW.event_type = 'campaign_first_yield' THEN
    IF NEW.points <> 15 OR NOT confirmed_yield_exists THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  -- Every future positive V5 campaign reward must explicitly define its proof.
  RETURN NULL;
END;
$$;

CREATE TRIGGER mi_olivo_v5_campaign_reward_guard
BEFORE INSERT ON mi_olivo_ledger
FOR EACH ROW
EXECUTE FUNCTION validate_mi_olivo_v5_campaign_reward();

COMMENT ON FUNCTION validate_mi_olivo_v5_campaign_reward() IS
  'Rejects Mi Olivo V5 positive campaign rewards unless the user really meets the campaign milestone.';

COMMIT;
