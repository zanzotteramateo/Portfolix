package com.portfolix.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Firma ID tokens "de Google" con una clave RSA de prueba, para que los tests no dependan de Google
 * ni de internet. {@link TestGoogleConfiguration} hace que la app los valide con la clave pública de prueba.
 */
public final class TestGoogle {

    public static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";

    private static final RSAKey KEY = newKey("test-google-key");

    private TestGoogle() {
    }

    /** Un ID token válido: emitido por Google, para Portfolix, vigente y con el mail verificado. */
    public static String idToken(String subject, String email) {
        return idToken(subject, email, claims -> {
        });
    }

    /** Un ID token que arranca válido; {@code changes} modifica los claims (ej.: otro {@code aud}). */
    public static String idToken(String subject, String email, Consumer<JwtClaimsSet.Builder> changes) {
        return sign(KEY, subject, email, changes);
    }

    /** Un ID token válido en todo, salvo que lo firmó otra clave: no lo emitió "Google". */
    public static String idTokenSignedWithAnotherKey(String subject, String email) {
        return sign(newKey("otra-clave"), subject, email, claims -> {
        });
    }

    /** Un decoder que valida con la clave pública de prueba, en vez de bajar las de Google. */
    public static NimbusJwtDecoder decoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String sign(RSAKey key, String subject, String email, Consumer<JwtClaimsSet.Builder> changes) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .claim("email", email)
                .claim("email_verified", true)
                .claim("name", "Juan Pérez");
        changes.accept(claims);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).type("JWT").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)))
                .encode(JwtEncoderParameters.from(header, claims.build()))
                .getTokenValue();
    }

    private static RSAKey newKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
