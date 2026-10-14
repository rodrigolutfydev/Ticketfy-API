ALTER TABLE refresh_tokens ADD COLUMN revoked_at TIMESTAMPTZ;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT ck_refresh_tokens_revoked_unused
        CHECK (revoked_at IS NULL OR used_at IS NULL);
