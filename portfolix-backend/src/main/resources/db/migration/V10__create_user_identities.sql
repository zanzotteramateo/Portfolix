-- Una cuenta que entra con Google puede no tener contraseña.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Cuentas externas (por ahora, Google) vinculadas a un usuario de Portfolix. Se identifican por el
-- "sub" del proveedor (un id fijo de la cuenta), no por el mail, que del lado del proveedor puede cambiar.
CREATE TABLE user_identities (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_identities_provider_subject UNIQUE (provider, subject),
    -- Como mucho una cuenta de cada proveedor por usuario. También sirve de índice por user_id.
    CONSTRAINT uq_user_identities_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_user_identities_provider CHECK (provider IN ('GOOGLE')),
    CONSTRAINT fk_user_identities_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
