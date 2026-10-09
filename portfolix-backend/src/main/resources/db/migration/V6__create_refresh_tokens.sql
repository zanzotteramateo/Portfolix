-- Una fila por sesión abierta (dispositivo). Se guarda solo el hash SHA-256 del token:
-- si se filtra la base, los tokens no se pueden usar.
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    remember_me BOOLEAN     NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL = activo. Se completa al rotarlo, al hacer logout o al cerrar todas las sesiones.
    revoked_at  TIMESTAMPTZ,

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Para revocar todas las sesiones de un usuario.
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
