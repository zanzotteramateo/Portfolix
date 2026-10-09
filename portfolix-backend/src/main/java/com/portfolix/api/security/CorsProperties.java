package com.portfolix.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Orígenes (front-ends) autorizados a llamar a la API desde el navegador.
 * Se configura en {@code portfolix.cors.allowed-origins}.
 */
@ConfigurationProperties(prefix = "portfolix.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
