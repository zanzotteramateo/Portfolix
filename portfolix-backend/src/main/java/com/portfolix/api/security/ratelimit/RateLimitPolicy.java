package com.portfolix.api.security.ratelimit;

/**
 * Grupos de endpoints con el mismo límite de pedidos por IP. Los endpoints de un grupo comparten
 * el contador: ej., registrarse y pedir el reenvío del mail descuentan del mismo límite (EMAIL).
 */
public enum RateLimitPolicy {

    /** Intentos de iniciar sesión. */
    LOGIN,

    /** Pedidos que mandan un mail: registro, reenvío del link, olvidé mi contraseña, cambio de mail. */
    EMAIL,

    /** Pedidos con el token de un link: verificar el mail, restablecer la contraseña. */
    TOKEN
}
