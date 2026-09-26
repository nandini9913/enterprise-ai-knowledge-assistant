-- Document metadata and sharing.

CREATE TABLE documents (
    id                  UUID          PRIMARY KEY,
    owner_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    filename            VARCHAR(255)  NOT NULL,
    document_type       VARCHAR(10)   NOT NULL CHECK (document_type IN ('PDF', 'DOCX')),
    content_type        VARCHAR(150)  NOT NULL,
    size_bytes          BIGINT        NOT NULL CHECK (size_bytes > 0),
    checksum_sha256     VARCHAR(64)   NOT NULL,
    storage_key         VARCHAR(512)  NOT NULL UNIQUE,
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    failure_reason      VARCHAR(1000),
    chunk_count         INTEGER       NOT NULL DEFAULT 0,
    processing_attempts INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ,
    version             BIGINT        NOT NULL DEFAULT 0,
    -- The same user uploading the same bytes twice is a duplicate, not a new document.
    CONSTRAINT ux_documents_owner_checksum UNIQUE (owner_id, checksum_sha256)
);

-- "My documents, newest first" and "documents in status X" (admin / stuck-job sweeps).
CREATE INDEX ix_documents_owner_created ON documents (owner_id, created_at DESC);
CREATE INDEX ix_documents_status_updated ON documents (status, updated_at);

CREATE TABLE document_access (
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    granted_by  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, user_id)
);

-- Supports the "shared with me" lookup used by listing and by vector search authorization.
CREATE INDEX ix_document_access_user ON document_access (user_id, document_id);
