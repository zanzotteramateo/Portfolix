CREATE TABLE portfolios (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Al borrar un usuario se borran sus portafolios.
    CONSTRAINT fk_portfolios_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Un usuario no puede tener dos portafolios con el mismo nombre ("Jubilación" = "jubilación").
-- Además sirve de índice para buscar los portafolios de un usuario.
CREATE UNIQUE INDEX uq_portfolios_user_name ON portfolios (user_id, lower(name));
