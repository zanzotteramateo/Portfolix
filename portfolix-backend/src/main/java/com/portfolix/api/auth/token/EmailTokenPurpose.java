package com.portfolix.api.auth.token;

/** Para qué sirve el link de un mail. */
public enum EmailTokenPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    /** Confirmar el mail nuevo de una cuenta: el token guarda ese mail. */
    EMAIL_CHANGE
}
