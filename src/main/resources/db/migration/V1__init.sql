-- Enable the pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create the documents table
CREATE TABLE documents (
    id UUID PRIMARY KEY,
    name TEXT,
    uploaded_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE documents IS 'Stores metadata about uploaded documents.';
COMMENT ON COLUMN documents.id IS 'Primary key for the document.';
COMMENT ON COLUMN documents.name IS 'Original filename of the document.';
COMMENT ON COLUMN documents.uploaded_at IS 'Timestamp when the document was uploaded.';


-- Create the chunks table
-- The embedding dimension is set to 1536, which is the dimension for OpenAI''s text-embedding-3-small model.
-- If you use a different embedding model, you may need to change this value.
CREATE TABLE chunks (
    id UUID PRIMARY KEY,
    document_id UUID, -- Removed foreign key
    content TEXT,
    content_vector VECTOR(1024),
    chunk_index INT,
    source_meta JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE chunks IS 'Stores document chunks, their embeddings, and metadata.';
COMMENT ON COLUMN chunks.document_id IS 'Foreign key to the parent document.';
COMMENT ON COLUMN chunks.content IS 'The text content of the chunk.';
COMMENT ON COLUMN chunks.content_vector IS 'The embedding vector for the content.';
COMMENT ON COLUMN chunks.chunk_index IS 'The 0-based index of the chunk within the document.';
COMMENT ON COLUMN chunks.source_meta IS 'Additional metadata, e.g., page number.';

-- Create an index on the content_vector for efficient similarity search.
-- The IVF Flat index is a good starting point. For larger datasets,
-- consider using HNSW (Hierarchical Navigable Small World).
-- The 'lists' parameter should be tuned based on the number of rows, a good starting point is rows/1000.
CREATE INDEX ON chunks USING ivfflat (content_vector vector_cosine_ops) WITH (lists = 100);

-- To use HNSW instead (generally better for performance and accuracy on large datasets):
-- CREATE INDEX ON chunks USING hnsw (content_vector vector_cosine_ops);
