package com.portfolix.api.auth.oauth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del login con Google. Se lee de {@code portfolix.auth.google}.
 *
 * @param clientId  Client ID de Google Cloud (OAuth, tipo "Aplicación web"). No es secreto: el front lo usa
 *                  en el botón. Si falta, el login con Google responde 503 y el resto de la app funciona igual
 * @param jwkSetUri dónde publica Google las claves públicas con las que firma los ID tokens
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.auth.google")
public record GoogleProperties(
        String clientId,
        @NotBlank String jwkSetUri
) {
}
