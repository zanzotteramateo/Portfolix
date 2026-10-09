package com.portfolix.api.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inyecta en un parámetro {@code String} de controller la sesión del usuario autenticado
 * (claim {@code sid} del access token), o {@code null} si el token no la tiene.
 * Sirve para distinguir "este dispositivo" de los demás: la cookie del refresh token no sirve
 * para eso porque solo viaja a {@code /api/v1/auth}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "getClaimAsString('" + JwtService.SESSION_CLAIM + "')")
public @interface CurrentSessionId {
}
