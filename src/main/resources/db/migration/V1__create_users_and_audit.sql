-- Users and audit trail.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    email         VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Case-insensitive uniqueness: "Alice" and "alice" are the same account.
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email ON users (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE audit_events (
    id            UUID         PRIMARY KEY,
    user_id       UUID,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64),
    resource_id   VARCHAR(128),
    detail        VARCHAR(500),
    request_id    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_events_created_at ON audit_events (created_at DESC);
CREATE INDEX ix_audit_events_user_created ON audit_events (user_id, created_at DESC);
