package com.portfolix.api.auth.token;

import java.time.Duration;
import java.util.UUID;

/**
 * Un refresh token recién emitido. {@code value} es el valor en claro que va en la cookie;
 * en la base solo queda su hash, así que esta es la única vez que existe.
 *
 * @param sessionId la sesión (dispositivo) a la que pertenece: va en el access token como {@code sid}
 */
public record IssuedRefreshToken(String value, Long userId, UUID sessionId, boolean rememberMe, Duration ttl) {
}
