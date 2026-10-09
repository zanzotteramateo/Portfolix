CREATE TABLE transactions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    portfolio_id BIGINT         NOT NULL,
    asset_id     BIGINT         NOT NULL,
    type         VARCHAR(4)     NOT NULL,
    quantity     NUMERIC(38, 18) NOT NULL,
    price        NUMERIC(38, 18) NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    trade_date   DATE           NOT NULL,
    notes        VARCHAR(500),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),

    -- Borrar un portafolio (o un usuario) borra sus transacciones.
    -- La opción "mover a otro portafolio" la resuelve el service antes de borrar.
    CONSTRAINT fk_transactions_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolios (id) ON DELETE CASCADE,
    -- No se puede borrar un activo que tiene transacciones.
    CONSTRAINT fk_transactions_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_transactions_type CHECK (type IN ('BUY', 'SELL')),
    CONSTRAINT ck_transactions_currency CHECK (currency IN ('ARS', 'USD')),
    CONSTRAINT ck_transactions_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_transactions_price_positive CHECK (price > 0)
);

-- Listado de transacciones de un portafolio ordenado por fecha.
CREATE INDEX ix_transactions_portfolio_date ON transactions (portfolio_id, trade_date DESC);
-- Historial de operaciones de un activo (panel de detalle).
CREATE INDEX ix_transactions_asset ON transactions (asset_id);
