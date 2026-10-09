package com.portfolix.api.common.exception;

/**
 * Se sabe quién es el usuario, pero no puede hacer esta operación (403).
 * Ej.: credenciales correctas en una cuenta que todavía no verificó su mail.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
