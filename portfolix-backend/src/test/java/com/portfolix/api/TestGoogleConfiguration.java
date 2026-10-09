package com.portfolix.api;

import com.portfolix.api.auth.oauth.GoogleIdTokenVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * En los tests, el login con Google valida los tokens de {@link TestGoogle} (clave y Client ID de prueba)
 * en vez de bajar las claves de Google. {@code @Primary}: entre este verificador y el real, se usa este.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestGoogleConfiguration {

    @Bean
    @Primary
    GoogleIdTokenVerifier testGoogleIdTokenVerifier() {
        return new GoogleIdTokenVerifier(TestGoogle.CLIENT_ID, TestGoogle.decoder());
    }
}
