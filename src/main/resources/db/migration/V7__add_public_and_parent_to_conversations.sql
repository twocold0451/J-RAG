ALTER TABLE conversations ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversations ADD COLUMN parent_id BIGINT;

CREATE INDEX idx_conversations_parent_id ON conversations (parent_id);
