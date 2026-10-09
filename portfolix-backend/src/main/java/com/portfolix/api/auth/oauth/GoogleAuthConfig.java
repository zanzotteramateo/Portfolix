package com.portfolix.api.auth.oauth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleAuthConfig {

    /**
     * El decoder baja las claves públicas de Google la primera vez que las necesita (no al arrancar)
     * y las cachea. El {@code RestTemplate} lleva los timeouts de {@code spring.http.clients}:
     * si Google no responde, el login falla rápido en vez de quedar colgado.
     */
    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(GoogleProperties properties, RestTemplateBuilder restTemplateBuilder) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .restOperations(restTemplateBuilder.build())
                .build();
        return new GoogleIdTokenVerifier(properties.clientId(), decoder);
    }
}
