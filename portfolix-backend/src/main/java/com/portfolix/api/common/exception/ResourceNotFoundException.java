package com.portfolix.api.common.exception;

/**
 * El recurso pedido no existe o no pertenece al usuario autenticado (404).
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
