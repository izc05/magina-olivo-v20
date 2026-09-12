BEGIN;

CREATE OR REPLACE FUNCTION validate_mi_olivo_v2_reward_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  source_slug TEXT;
  content_type TEXT;
  record_id UUID;
  source_exists BOOLEAN := false;
BEGIN
  IF NEW.rule_version <> 'mi-olivo-v2' OR NEW.points <= 0 THEN
    RETURN NEW;
  END IF;

  IF NEW.event_type = 'territory_viewed' THEN
    IF NEW.source_id !~ '^pueblo:[a-z0-9]+(?:-[a-z0-9]+)*$' THEN
      RETURN NULL;
    END IF;

    source_slug := substring(NEW.source_id FROM 8);
    SELECT EXISTS (
      SELECT 1
      FROM territory_places p
      JOIN territory_municipalities m ON m.id = p.municipality_id
      WHERE p.slug = source_slug
        AND p.public_enabled = true
        AND m.active = true
    ) INTO source_exists;

    IF NOT source_exists THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  IF NEW.event_type = 'content_read' THEN
    IF NEW.source_id ~ '^noticia:[a-z0-9]+(?:-[a-z0-9]+)*$' THEN
      content_type := 'news';
      source_slug := substring(NEW.source_id FROM 9);
    ELSIF NEW.source_id ~ '^evento:[a-z0-9]+(?:-[a-z0-9]+)*$' THEN
      content_type := 'event';
      source_slug := substring(NEW.source_id FROM 8);
    ELSE
      RETURN NULL;
    END IF;

    SELECT EXISTS (
      SELECT 1
      FROM cms_entries
      WHERE type = content_type
        AND slug = source_slug
        AND status = 'published'
        AND (starts_at IS NULL OR starts_at <= now())
        AND (ends_at IS NULL OR ends_at >= now())
    ) INTO source_exists;

    IF NOT source_exists THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  IF NEW.event_type = 'learning_completed' THEN
    source_exists := NEW.source_id = ANY (ARRAY[
      'consejo:recorrido-observacion',
      'consejo:antes-de-regar',
      'consejo:antes-de-abonar',
      'consejo:antes-de-tratar',
      'consejo:planificar-poda',
      'consejo:preparar-cosecha',
      'consejo:despues-episodio',
      'consejo:trabajo-seguro'
    ]::TEXT[]);

    IF NOT source_exists THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  IF NEW.event_type = 'weather_checked' THEN
    IF NEW.source_id <> 'radar' THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  IF NEW.event_type = 'field_activity_reward' THEN
    IF NEW.source_id IS NULL OR NEW.source_id !~ '^[0-9a-fA-F-]{36}$' THEN
      RETURN NULL;
    END IF;

    BEGIN
      record_id := NEW.source_id::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
      RETURN NULL;
    END;

    CASE NEW.source_type
      WHEN 'irrigation_record' THEN
        SELECT EXISTS (
          SELECT 1 FROM irrigation_records
          WHERE id = record_id AND workspace_id = NEW.workspace_id AND created_by = NEW.user_id
        ) INTO source_exists;
      WHEN 'treatment_record' THEN
        SELECT EXISTS (
          SELECT 1 FROM treatment_records
          WHERE id = record_id AND workspace_id = NEW.workspace_id AND created_by = NEW.user_id
        ) INTO source_exists;
      WHEN 'fertilization_record' THEN
        SELECT EXISTS (
          SELECT 1 FROM fertilization_records
          WHERE id = record_id AND workspace_id = NEW.workspace_id AND created_by = NEW.user_id
        ) INTO source_exists;
      WHEN 'pruning_record' THEN
        SELECT EXISTS (
          SELECT 1 FROM pruning_records
          WHERE id = record_id AND workspace_id = NEW.workspace_id AND created_by = NEW.user_id
        ) INTO source_exists;
      WHEN 'observation_record' THEN
        SELECT EXISTS (
          SELECT 1 FROM observation_records
          WHERE id = record_id AND workspace_id = NEW.workspace_id AND created_by = NEW.user_id
        ) INTO source_exists;
      WHEN 'expense_record' THEN
        SELECT EXISTS (
          SELECT 1 FROM expense_records
          WHERE id = record_id AND workspace_id = NEW.workspace_id AND created_by = NEW.user_id
        ) INTO source_exists;
      ELSE
        RETURN NULL;
    END CASE;

    IF NOT source_exists THEN RETURN NULL; END IF;
    RETURN NEW;
  END IF;

  -- Every future V2 reward type must explicitly define how its source is verified.
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS mi_olivo_v2_reward_source_guard ON mi_olivo_ledger;
CREATE TRIGGER mi_olivo_v2_reward_source_guard
BEFORE INSERT ON mi_olivo_ledger
FOR EACH ROW
EXECUTE FUNCTION validate_mi_olivo_v2_reward_source();

COMMENT ON FUNCTION validate_mi_olivo_v2_reward_source() IS
  'Rejects Mi Olivo V2 positive ledger entries whose source does not exist or is not eligible.';

COMMIT;
