CREATE TABLE auth_sessions (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    last_used_at   TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(20),

    CONSTRAINT fk_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_auth_sessions_expires_at
        CHECK (expires_at > created_at),

    CONSTRAINT ck_auth_sessions_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'LOGOUT_ALL', 'PASSWORD_CHANGED', 'REUSE_DETECTED')),

    CONSTRAINT ck_auth_sessions_revoked
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

CREATE INDEX idx_auth_sessions_user_active ON auth_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);
CREATE INDEX idx_auth_sessions_revoked_at ON auth_sessions (revoked_at) WHERE revoked_at IS NOT NULL;

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    session_id  UUID        NOT NULL,
    token_hash  BYTEA       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    replaced_by UUID,

    CONSTRAINT fk_refresh_tokens_session
        FOREIGN KEY (session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE,

    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by) REFERENCES refresh_tokens (id) ON DELETE CASCADE,

    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),

    CONSTRAINT ck_refresh_tokens_token_hash
        CHECK (octet_length(token_hash) = 32),

    CONSTRAINT ck_refresh_tokens_expires_at
        CHECK (expires_at > created_at),

    CONSTRAINT ck_refresh_tokens_used
        CHECK ((used_at IS NULL) = (replaced_by IS NULL))
);

CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id);
