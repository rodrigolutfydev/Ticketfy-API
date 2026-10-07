ALTER TABLE users
    ADD COLUMN avatar_url VARCHAR(500);

ALTER TABLE users
    ADD CONSTRAINT chk_users_avatar_url_https
        CHECK (avatar_url IS NULL OR avatar_url LIKE 'https://%');
