package com.portfolix.api.auth.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de los refresh tokens y su cookie. Se lee de {@code portfolix.auth.refresh-token}.
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.auth.refresh-token")
public record RefreshTokenProperties(
        @NotNull Duration rememberMeTtl,
        @NotNull Duration sessionTtl,
        @NotBlank String cookieName,
        boolean cookieSecure
) {

    public Duration ttl(boolean rememberMe) {
        return rememberMe ? rememberMeTtl : sessionTtl;
    }
}
