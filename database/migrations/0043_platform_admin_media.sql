CREATE TABLE IF NOT EXISTS platform_media_assets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  storage_key text NOT NULL UNIQUE,
  original_filename text NOT NULL,
  mime_type text NOT NULL CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/avif')),
  byte_size bigint NOT NULL CHECK (byte_size > 0 AND byte_size <= 10485760),
  sha256 text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
  status text NOT NULL DEFAULT 'reserved' CHECK (status IN ('reserved', 'uploaded', 'failed', 'archived')),
  created_by uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  uploaded_at timestamptz,
  storage_etag text,
  storage_checksum_sha256 text
);

CREATE INDEX IF NOT EXISTS idx_platform_media_assets_status_created
  ON platform_media_assets (status, created_at DESC);

COMMENT ON TABLE platform_media_assets IS 'Corporate media library for public Mágina Olivo website assets.';
COMMENT ON COLUMN platform_media_assets.storage_key IS 'Private object-storage key. Public consumers use /api/v1/public/media/:id instead of the key.';
