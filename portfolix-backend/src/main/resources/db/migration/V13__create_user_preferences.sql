-- Preferencias de la app: las de la pantalla "Ajustes" (tema, separador decimal) y las de la cabecera
-- del dashboard (moneda, ocultar montos). Una fila por usuario, que se crea la primera vez que guarda
-- algo: hasta entonces valen los valores por defecto (ver UserPreferences).
CREATE TABLE user_preferences (
    user_id           BIGINT PRIMARY KEY,
    theme             VARCHAR(10) NOT NULL,
    decimal_separator VARCHAR(10) NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    hide_amounts      BOOLEAN     NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_user_preferences_theme CHECK (theme IN ('LIGHT', 'DARK')),
    CONSTRAINT ck_user_preferences_decimal_separator CHECK (decimal_separator IN ('COMMA', 'PERIOD')),
    CONSTRAINT ck_user_preferences_currency CHECK (currency IN ('ARS', 'USD')),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
