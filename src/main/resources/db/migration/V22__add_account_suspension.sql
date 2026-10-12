ALTER TABLE users
    ADD COLUMN suspended_at      TIMESTAMPTZ,
    ADD COLUMN suspension_reason VARCHAR(500),
    ADD COLUMN suspended_by      UUID;

ALTER TABLE users
    ADD CONSTRAINT fk_users_suspended_by
        FOREIGN KEY (suspended_by) REFERENCES users (id);

ALTER TABLE users
    ADD CONSTRAINT ck_users_suspension
        CHECK ((suspended_at IS NULL) = (suspension_reason IS NULL)
           AND (suspended_at IS NULL) = (suspended_by IS NULL));

ALTER TABLE users
    ADD CONSTRAINT ck_users_suspension_not_deleted
        CHECK (deleted_at IS NULL OR suspended_at IS NULL);

ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_revoked_reason;

ALTER TABLE auth_sessions
    ADD CONSTRAINT ck_auth_sessions_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'LOGOUT_ALL', 'PASSWORD_CHANGED', 'REUSE_DETECTED', 'ACCOUNT_DELETED',
                                  'ACCOUNT_SUSPENDED'));
