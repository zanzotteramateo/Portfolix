package com.portfolix.api.common.exception;

/**
 * El recurso cambió mientras se procesaba el pedido (409): conviene volver a cargarlo e intentar de nuevo.
 * Ej.: se edita una transacción que otro pedido movió a otro portafolio en el mismo momento.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
