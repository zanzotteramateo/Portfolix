package com.portfolix.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La fecha no puede ser posterior a hoy en la zona horaria del negocio (ver BusinessCalendar).
 * Reemplaza a {@code @PastOrPresent}, que usa la zona del servidor. Un valor nulo es válido.
 */
@Documented
@Constraint(validatedBy = NotFutureDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotFutureDate {

    String message() default "La fecha no puede ser futura";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
