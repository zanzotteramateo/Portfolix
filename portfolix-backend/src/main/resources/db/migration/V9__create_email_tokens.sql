-- Tokens de un solo uso que viajan en los links de los mails (verificar la cuenta, restablecer
-- la contraseña). Igual que con los refresh tokens, se guarda solo el hash SHA-256.
-- Cada usuario tiene como máximo un token pendiente por propósito: al mandar un mail nuevo,
-- el pendiente anterior se borra y su link deja de servir.
CREATE TABLE email_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    purpose    VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    -- NULL = pendiente. Los usados se conservan: abrir dos veces el link de verificación no es un error.
    used_at    TIMESTAMPTZ,

    CONSTRAINT uq_email_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT fk_email_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Para borrar el pendiente de un usuario y saber cuándo se le mandó el último mail.
CREATE INDEX ix_email_tokens_user_purpose ON email_tokens (user_id, purpose);
