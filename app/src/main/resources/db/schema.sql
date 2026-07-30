CREATE TABLE IF NOT EXISTS clip_entries (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  content        TEXT    NOT NULL,
  content_norm   TEXT    NOT NULL,
  content_hash   TEXT    NOT NULL,
  is_favorite    INTEGER NOT NULL DEFAULT 0,
  created_at     INTEGER NOT NULL,
  last_copied_at INTEGER NOT NULL DEFAULT 0,
  use_count      INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_clip_created_at
ON clip_entries(created_at DESC);
