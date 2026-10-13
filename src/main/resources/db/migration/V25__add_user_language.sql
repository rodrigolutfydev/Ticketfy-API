ALTER TABLE users
    ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'pt-BR';

ALTER TABLE users
    ADD CONSTRAINT ck_users_language
        CHECK (language IN ('pt-BR', 'en'));
