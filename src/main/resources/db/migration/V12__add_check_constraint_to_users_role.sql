ALTER TABLE users
    ADD CONSTRAINT ck_users_role
        CHECK (role IN ('USER', 'ORGANIZER', 'ADMIN'));
