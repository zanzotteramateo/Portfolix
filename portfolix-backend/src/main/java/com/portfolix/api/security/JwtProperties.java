package com.portfolix.api.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración del access token (JWT). Se lee de {@code portfolix.jwt}.
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.jwt")
public record JwtProperties(
        @NotBlank(message = "Falta la clave de firma de JWT (variable de entorno JWT_SECRET)") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl
) {
}
