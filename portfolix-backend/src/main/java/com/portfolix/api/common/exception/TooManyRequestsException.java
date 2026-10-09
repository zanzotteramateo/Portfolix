package com.portfolix.api.common.exception;

import java.time.Duration;

/**
 * Demasiados pedidos o intentos: hay que esperar antes de reintentar (429).
 * La respuesta lleva el header {@code Retry-After} con los segundos de espera,
 * para que el front pueda mostrar una cuenta regresiva.
 */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
