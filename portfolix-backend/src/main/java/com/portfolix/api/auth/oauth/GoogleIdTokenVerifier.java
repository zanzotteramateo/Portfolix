package com.portfolix.api.auth.oauth;

import com.portfolix.api.common.exception.ForbiddenException;
import com.portfolix.api.common.exception.ServiceUnavailableException;
import com.portfolix.api.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

/**
 * Valida el ID token que el front obtiene con el botón "Continuar con Google" (Google Identity Services).
 * <p>
 * El ID token es un JWT firmado por Google. Se verifica:
 * <ul>
 *   <li>la firma, con las claves públicas de Google (el decoder las baja y las cachea);</li>
 *   <li>que lo haya emitido Google ({@code iss}) y que sea para Portfolix ({@code aud} = nuestro Client ID).
 *       Sin el {@code aud}, un token que otra app obtuvo para su propio login serviría para entrar acá;</li>
 *   <li>que no esté vencido ({@code exp}; viven una hora);</li>
 *   <li>que Google haya verificado el mail ({@code email_verified}).</li>
 * </ul>
 * El {@code NimbusJwtDecoder} de este verificador no es un bean: si lo fuera, Spring Security tendría dos
 * decoders y no sabría cuál usar para los access tokens de la API.
 */
public class GoogleIdTokenVerifier {

    static final String INVALID_TOKEN_MESSAGE = "No pudimos validar tu inicio de sesión con Google. Probá de nuevo";
    static final String UNVERIFIED_EMAIL_MESSAGE = "Tu cuenta de Google no tiene el correo verificado";
    static final String UNAVAILABLE_MESSAGE = "El inicio de sesión con Google no está disponible en este momento";

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);
    /** Google firma con cualquiera de los dos. */
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private final NimbusJwtDecoder decoder;

    public GoogleIdTokenVerifier(String clientId, NimbusJwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
        // Además de estos dos, los validadores por defecto revisan el vencimiento y el tipo de token.
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        issuer -> issuer != null && GOOGLE_ISSUERS.contains(issuer.toString())),
                new JwtAudienceValidator(clientId == null ? "" : clientId)));
    }

    /**
     * Devuelve los datos de la cuenta de Google. 401 si el token no es válido, 403 si Google no verificó
     * el mail, 503 si falta el Client ID o no se pudieron bajar las claves de Google.
     */
    public ExternalAccount verify(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            log.warn("Login con Google sin configurar: falta portfolix.auth.google.client-id (GOOGLE_CLIENT_ID)");
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
        Jwt jwt = decode(idToken);
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new ForbiddenException(UNVERIFIED_EMAIL_MESSAGE);
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || !email.contains("@")) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }
        return new ExternalAccount(IdentityProvider.GOOGLE, jwt.getSubject(), email, name);
    }

    private Jwt decode(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (BadJwtException ex) {
            // Firma, emisor, destinatario o vencimiento inválidos, o directamente no es un JWT.
            log.debug("ID token de Google rechazado: {}", ex.getMessage());
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        } catch (JwtException ex) {
            // No es culpa del token: no se pudieron bajar las claves públicas de Google.
            log.warn("No se pudieron obtener las claves públicas de Google", ex);
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }
}
