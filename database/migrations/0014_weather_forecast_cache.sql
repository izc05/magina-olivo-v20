BEGIN;

CREATE TABLE weather_forecast_cache (
  municipality_id UUID NOT NULL REFERENCES territory_municipalities(id) ON DELETE CASCADE,
  provider TEXT NOT NULL CHECK (provider IN ('aemet_daily')),
  payload_json JSONB NOT NULL,
  fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  last_error_at TIMESTAMPTZ,
  last_error_code TEXT,
  PRIMARY KEY (municipality_id, provider)
);

CREATE INDEX weather_forecast_cache_expiry_idx ON weather_forecast_cache(expires_at);

COMMENT ON TABLE weather_forecast_cache IS 'Shared short-lived cache of public municipality forecasts. It prevents one upstream AEMET request per end-user request.';

COMMIT;
