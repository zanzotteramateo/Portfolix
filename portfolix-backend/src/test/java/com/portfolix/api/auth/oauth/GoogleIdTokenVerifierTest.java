package com.portfolix.api.auth.oauth;

import com.portfolix.api.TestGoogle;
import com.portfolix.api.common.exception.ForbiddenException;
import com.portfolix.api.common.exception.ServiceUnavailableException;
import com.portfolix.api.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La validación real de Nimbus, con tokens firmados por la clave de prueba de TestGoogle. */
class GoogleIdTokenVerifierTest {

    private static final String SUBJECT = "109876543210987654321";
    private static final String EMAIL = "juan@gmail.com";

    private final GoogleIdTokenVerifier verifier =
            new GoogleIdTokenVerifier(TestGoogle.CLIENT_ID, TestGoogle.decoder());

    @Test
    void validToken_returnsTheGoogleAccount() {
        ExternalAccount account = verifier.verify(TestGoogle.idToken(SUBJECT, EMAIL));

        assertThat(account).isEqualTo(new ExternalAccount(IdentityProvider.GOOGLE, SUBJECT, EMAIL, "Juan Pérez"));
    }

    @Test
    void issuerWithoutHttps_isAlsoGoogle() {
        String token = TestGoogle.idToken(SUBJECT, EMAIL, claims -> claims.issuer("accounts.google.com"));

        assertThat(verifier.verify(token).subject()).isEqualTo(SUBJECT);
    }

    @Test
    void tokenObtainedForAnotherApp_isRejected() {
        String token = TestGoogle.idToken(SUBJECT, EMAIL, claims -> claims.audience(List.of("otra-app")));

        assertRejected(token);
    }

    @Test
    void tokenFromAnotherIssuer_isRejected() {
        String token = TestGoogle.idToken(SUBJECT, EMAIL, claims -> claims.issuer("https://evil.example.com"));

        assertRejected(token);
    }

    @Test
    void expiredToken_isRejected() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        String token = TestGoogle.idToken(SUBJECT, EMAIL, claims -> claims
                .issuedAt(twoHoursAgo.minus(Duration.ofHours(1)))
                .expiresAt(twoHoursAgo));

        assertRejected(token);
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        assertRejected(TestGoogle.idTokenSignedWithAnotherKey(SUBJECT, EMAIL));
    }

    @Test
    void somethingThatIsNotAJwt_isRejected() {
        assertRejected("no-es-un-jwt");
    }

    @Test
    void emailNotVerifiedByGoogle_isForbidden() {
        String token = TestGoogle.idToken(SUBJECT, EMAIL, claims -> claims.claim("email_verified", false));

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(GoogleIdTokenVerifier.UNVERIFIED_EMAIL_MESSAGE);
    }

    @Test
    void withoutName_usesWhatComesBeforeTheAtOfTheEmail() {
        String token = TestGoogle.idToken(SUBJECT, EMAIL, claims -> claims.claims(map -> map.remove("name")));

        assertThat(verifier.verify(token).name()).isEqualTo("juan");
    }

    @Test
    void withoutClientId_googleLoginIsUnavailable() {
        GoogleIdTokenVerifier notConfigured = new GoogleIdTokenVerifier("", TestGoogle.decoder());

        assertThatThrownBy(() -> notConfigured.verify(TestGoogle.idToken(SUBJECT, EMAIL)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void whenGoogleKeysCannotBeDownloaded_googleLoginIsUnavailable() {
        // Nada escucha en el puerto 1: la descarga de las claves falla enseguida, sin salir a internet.
        NimbusJwtDecoder unreachable = NimbusJwtDecoder.withJwkSetUri("http://localhost:1/certs").build();
        GoogleIdTokenVerifier withoutKeys = new GoogleIdTokenVerifier(TestGoogle.CLIENT_ID, unreachable);

        assertThatThrownBy(() -> withoutKeys.verify(TestGoogle.idToken(SUBJECT, EMAIL)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(GoogleIdTokenVerifier.INVALID_TOKEN_MESSAGE);
    }
}
