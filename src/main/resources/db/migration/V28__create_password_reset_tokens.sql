-- Tokens de recuperação de senha. Só o hash SHA-256 do token fica no banco;
-- o valor em claro vai apenas no link do e-mail.
CREATE TABLE password_reset_tokens (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL,
    token_hash     BYTEA       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash),

    CONSTRAINT ck_password_reset_tokens_token_hash
        CHECK (octet_length(token_hash) = 32),

    CONSTRAINT ck_password_reset_tokens_expires_at
        CHECK (expires_at > created_at),

    -- Um token termina de um jeito só: usado ou invalidado por um pedido novo.
    CONSTRAINT ck_password_reset_tokens_closed
        CHECK (used_at IS NULL OR invalidated_at IS NULL)
);

CREATE INDEX idx_password_reset_tokens_user_open
    ON password_reset_tokens (user_id)
    WHERE used_at IS NULL AND invalidated_at IS NULL;

CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
