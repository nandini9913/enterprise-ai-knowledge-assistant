-- Searchable chunks with embeddings (pgvector).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    id             UUID         PRIMARY KEY,
    document_id    UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index    INTEGER      NOT NULL,
    content        TEXT         NOT NULL,
    page_number    INTEGER,
    char_start     INTEGER      NOT NULL,
    char_end       INTEGER      NOT NULL,
    token_estimate INTEGER      NOT NULL,
    -- Must match app.ai.embedding.dimensions (Titan Text Embeddings V2 at 1024 dimensions).
    embedding      vector(1024) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_document_chunks_document_index UNIQUE (document_id, chunk_index)
);

-- Approximate nearest-neighbour index for cosine distance (the <=> operator).
-- HNSW: better recall/latency trade-off than IVFFlat and no training step, at the cost of
-- slower builds and more memory. m / ef_construction are pgvector's defaults, made explicit.
CREATE INDEX ix_document_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
