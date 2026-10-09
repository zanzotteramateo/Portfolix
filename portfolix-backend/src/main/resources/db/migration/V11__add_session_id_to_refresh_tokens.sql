-- Cada login abre una sesión (un dispositivo). Al rotar el refresh token se crea una fila nueva,
-- pero la sesión sigue siendo la misma: session_id la identifica. El access token la lleva en el
-- claim "sid", así se sabe desde qué sesión viene cada pedido (ej.: para cerrar las otras al cambiar
-- la contraseña).
ALTER TABLE refresh_tokens ADD COLUMN session_id UUID;

-- Las filas que ya existían: cada una es su propia sesión.
UPDATE refresh_tokens SET session_id = gen_random_uuid();

ALTER TABLE refresh_tokens ALTER COLUMN session_id SET NOT NULL;
