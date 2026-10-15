-- Login com Google: a conta pode existir sem senha (criada pelo Google) e
-- guarda o identificador estável do Google (claim "sub" do id_token).
ALTER TABLE users
    ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN google_subject VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT uk_users_google_subject UNIQUE (google_subject);

-- Toda conta ativa precisa de pelo menos um jeito de entrar: senha ou Google.
ALTER TABLE users
    ADD CONSTRAINT ck_users_sign_in_method
        CHECK (deleted_at IS NOT NULL OR password IS NOT NULL OR google_subject IS NOT NULL);

-- Ao ligar ao Google uma conta que já existia (achada pelo e-mail), as sessões
-- abertas dela são encerradas com o motivo GOOGLE_LINKED.
ALTER TABLE auth_sessions DROP CONSTRAINT ck_auth_sessions_revoked_reason;

ALTER TABLE auth_sessions
    ADD CONSTRAINT ck_auth_sessions_revoked_reason
        CHECK (revoked_reason IN ('LOGOUT', 'LOGOUT_ALL', 'PASSWORD_CHANGED', 'REUSE_DETECTED', 'ACCOUNT_DELETED',
                                  'ACCOUNT_SUSPENDED', 'GOOGLE_LINKED'));
