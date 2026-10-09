package com.portfolix.api.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración del bloqueo por intentos fallidos. Se lee de {@code portfolix.auth.login-lockout}.
 *
 * @param maxAttempts  contraseñas incorrectas seguidas con un mismo mail que bloquean el acceso
 * @param lockDuration cuánto dura el bloqueo. Es también cuánto tarda en olvidarse un error:
 *                     si pasa este tiempo sin errores nuevos, el contador vuelve a cero
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.auth.login-lockout")
public record LoginLockoutProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration lockDuration
) {
}
