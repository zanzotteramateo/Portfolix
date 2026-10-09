-- Catálogo global de activos (no pertenece a ningún usuario). El seed se carga en la fase 5.
CREATE TABLE assets (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol   VARCHAR(20)  NOT NULL,
    name     VARCHAR(100) NOT NULL,
    type     VARCHAR(10)  NOT NULL,
    -- Moneda en la que se opera el activo: las transacciones la copian al crearse.
    currency VARCHAR(3)   NOT NULL,
    active   BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_assets_symbol UNIQUE (symbol),
    CONSTRAINT ck_assets_type CHECK (type IN ('STOCK', 'CEDEAR', 'CRYPTO')),
    CONSTRAINT ck_assets_currency CHECK (currency IN ('ARS', 'USD'))
);
