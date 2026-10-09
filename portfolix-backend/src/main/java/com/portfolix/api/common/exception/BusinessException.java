package com.portfolix.api.common.exception;

/**
 * Una regla de negocio impide completar la operación (400).
 * Si la regla corresponde a un campo del formulario (ej.: "quantity" en una venta que supera
 * la tenencia), se indica en {@code field} y la respuesta lo incluye en {@code fieldErrors}.
 */
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        this(null, message);
    }

    public BusinessException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
