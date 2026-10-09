package com.portfolix.api.common.validation;

import com.portfolix.api.common.BusinessCalendar;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

/**
 * Spring crea los validadores e inyecta sus dependencias, por eso puede recibir el BusinessCalendar.
 */
public class NotFutureDateValidator implements ConstraintValidator<NotFutureDate, LocalDate> {

    private final BusinessCalendar calendar;

    public NotFutureDateValidator(BusinessCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public boolean isValid(LocalDate date, ConstraintValidatorContext context) {
        return date == null || !date.isAfter(calendar.today());
    }
}
