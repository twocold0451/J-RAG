ALTER TABLE documents ADD COLUMN user_id BIGINT;
-- Removed FK constraint

-- Update existing documents to belong to the first user (if any) or leave null if strictness allows
-- For now, we assume user_id can be nullable for old records, or we could set a default.
-- Let's make it nullable for safety with existing data, but strictly speaking it should be NOT NULL for logic.
