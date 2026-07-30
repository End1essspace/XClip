CREATE TABLE IF NOT EXISTS clip_entries (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  content        TEXT    NOT NULL,
  content_norm   TEXT    NOT NULL,
  content_hash   TEXT    NOT NULL,
  title          TEXT,
  is_favorite    INTEGER NOT NULL DEFAULT 0,
  pin_order      INTEGER,
  created_at     INTEGER NOT NULL,
  last_copied_at INTEGER NOT NULL DEFAULT 0,
  use_count      INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_clip_created_at
ON clip_entries(created_at DESC);

CREATE TABLE IF NOT EXISTS tags (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT    NOT NULL,
  name_norm  TEXT    NOT NULL,
  created_at INTEGER NOT NULL,
  CONSTRAINT ck_tags_name_length CHECK (length(name) BETWEEN 1 AND 64),
  CONSTRAINT uq_tags_name_norm UNIQUE (name_norm)
);

CREATE TABLE IF NOT EXISTS clip_tags (
  clip_id     INTEGER NOT NULL,
  tag_id      INTEGER NOT NULL,
  assigned_at INTEGER NOT NULL,
  PRIMARY KEY (clip_id, tag_id),
  FOREIGN KEY (clip_id) REFERENCES clip_entries(id) ON DELETE CASCADE,
  FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tags_name
ON tags(name COLLATE NOCASE, id);

CREATE INDEX IF NOT EXISTS idx_clip_tags_tag_id
ON clip_tags(tag_id, clip_id);
