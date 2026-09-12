BEGIN;

CREATE TABLE mi_olivo_appearance (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  selected_badge TEXT NOT NULL DEFAULT 'none'
    CHECK (selected_badge IN ('none', 'roots', 'harvest', 'explorer', 'campaign')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
