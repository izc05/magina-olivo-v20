BEGIN;

CREATE TABLE user_followed_towns (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  municipality_id UUID NOT NULL REFERENCES territory_municipalities(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, municipality_id)
);

CREATE INDEX idx_user_followed_towns_municipality ON user_followed_towns(municipality_id);

COMMENT ON TABLE user_followed_towns IS 'Municipalities followed by a user for local content. The single preferred_municipality preference remains the primary town.';

COMMIT;
