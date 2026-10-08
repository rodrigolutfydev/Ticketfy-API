ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT ck_users_deleted_anonymized
        CHECK (deleted_at IS NULL
            OR (avatar_url IS NULL
                AND email = 'deleted-' || id::text || '@deleted.ticketfy.invalid'));

ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_revoked_reason;

ALTER TABLE auth_sessions
    ADD CONSTRAINT ck_auth_sessions_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'LOGOUT_ALL', 'PASSWORD_CHANGED', 'REUSE_DETECTED', 'ACCOUNT_DELETED'));
