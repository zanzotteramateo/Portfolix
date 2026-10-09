CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    full_name         VARCHAR(100) NOT NULL,
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    terms_accepted_at TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    -- El mail se guarda siempre en minúsculas: así el UNIQUE también es case-insensitive.
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);
