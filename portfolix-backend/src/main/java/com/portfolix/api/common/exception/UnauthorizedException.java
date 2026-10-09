package com.portfolix.api.common.exception;

/**
 * No se pudo identificar al usuario: credenciales incorrectas o refresh token inválido (401).
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
