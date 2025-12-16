-- 1. Add column to store pre-segmented text (e.g. "我 爱 北京 天安门")
ALTER TABLE chunks ADD COLUMN content_keywords TEXT;

-- 2. Add generated tsvector column that automatically syncs with content_keywords using 'simple' tokenizer (splits by whitespace)
ALTER TABLE chunks ADD COLUMN content_search tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content_keywords, ''))) STORED;

-- 3. Create GIN index for fast full-text search
CREATE INDEX idx_chunks_content_search ON chunks USING GIN(content_search);
