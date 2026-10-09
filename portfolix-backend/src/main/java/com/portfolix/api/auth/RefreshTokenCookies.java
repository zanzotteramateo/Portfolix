package com.portfolix.api.auth;

import com.portfolix.api.auth.token.IssuedRefreshToken;
import com.portfolix.api.auth.token.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.util.Optional;

/**
 * Arma y lee la cookie del refresh token:
 * <ul>
 *   <li>{@code HttpOnly}: el JavaScript del front no la puede leer (protege contra XSS).</li>
 *   <li>{@code Secure}: solo viaja por HTTPS (los navegadores aceptan http://localhost como seguro).</li>
 *   <li>{@code SameSite=Strict}: el navegador no la manda en requests iniciados desde otros sitios (protege contra CSRF).</li>
 *   <li>{@code Path=/api/v1/auth}: solo se manda a los endpoints de auth, no en cada request.</li>
 * </ul>
 * Con "Recuérdame" la cookie tiene Max-Age y sobrevive al cierre del navegador; sin él es de sesión.
 */
@Component
public class RefreshTokenCookies {

    static final String PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    public RefreshTokenCookies(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(IssuedRefreshToken token) {
        ResponseCookie.ResponseCookieBuilder builder = base(token.value());
        if (token.rememberMe()) {
            builder.maxAge(token.ttl());
        }
        return builder.build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    public Optional<String> read(HttpServletRequest request) {
        return Optional.ofNullable(WebUtils.getCookie(request, properties.cookieName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank());
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
    }
}
