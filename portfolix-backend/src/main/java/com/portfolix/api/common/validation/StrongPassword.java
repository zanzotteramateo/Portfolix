package com.portfolix.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contraseña válida para Portfolix: entre 8 y 64 caracteres, con al menos una mayúscula,
 * un número y un símbolo. El máximo existe porque BCrypt solo usa los primeros 72 bytes.
 * Un valor nulo se considera inválido.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "La contraseña debe tener entre 8 y 64 caracteres, una mayúscula, un número y un símbolo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
