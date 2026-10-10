package com.portfolix.api.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend",
            "/api/v1/auth/verify-email/change-email",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/oauth/google",
            "/api/v1/auth/email-change/confirm",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler) throws Exception {
        http
                // Usa el bean corsConfigurationSource de abajo.
                .cors(Customizer.withDefaults())
                // El access token viaja en un header, no en una cookie: no hay riesgo de CSRF en esos endpoints.
                // La cookie del refresh token es SameSite=Strict y solo se manda a /api/v1/auth.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // El resto de la API exige sesión; lo que no es /api (el front ya compilado que
                        // sirve SpaWebConfig, cuando el deploy es de un solo origen) queda público: lo
                        // que requiere login lo controla React mostrando el login, no el servidor.
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // Lo exige el botón de Google en producción (ver la fase 8C): sin este header, en un
                // origen que no sea localhost, Google Identity Services no manda las credenciales.
                .headers(headers -> headers.referrerPolicy(
                        referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER_WHEN_DOWNGRADE)))
                // Valida el JWT del header Authorization con el JwtDecoder de JwtConfig.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errorHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    /** BCrypt por defecto; el prefijo {bcrypt} en el hash permite cambiar de algoritmo sin romper los existentes. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Headers de la respuesta que el JavaScript del front puede leer (los demás el navegador los oculta).
        // Retry-After: segundos a esperar después de un 429.
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.RETRY_AFTER));
        // Necesario para que el navegador mande y guarde la cookie del refresh token.
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
