package com.portfolix.api.common.exception;

/**
 * Un servicio externo del que depende la respuesta no está disponible (503).
 * Ej.: nunca se pudo obtener una cotización desde que arrancó la app.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
