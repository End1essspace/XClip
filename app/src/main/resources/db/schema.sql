CREATE TABLE IF NOT EXISTS clip_entries (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  content      TEXT    NOT NULL,
  content_norm TEXT    NOT NULL,
  content_hash TEXT    NOT NULL,
  is_favorite  INTEGER NOT NULL DEFAULT 0,
  created_at   INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_clip_created_at ON clip_entries(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_clip_hash       ON clip_entries(content_hash);
CREATE INDEX IF NOT EXISTS idx_clip_fav_created ON clip_entries(is_favorite, created_at DESC);
