package com.portfolix.api.common.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "Inversion2026!",
            "Ñandú#2026",          // mayúscula con tilde
            "Abcdef1.",            // exactamente 8
    })
    void acceptsValidPasswords(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "Ab1!xyz",             // 7 caracteres
            "inversion2026!",      // sin mayúscula
            "Inversion!!!",        // sin número
            "Inversion2026",       // sin símbolo
            "Inversion 2026",      // el espacio no cuenta como símbolo
    })
    void rejectsInvalidPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void acceptsUpTo64Characters() {
        String exactly64 = "Aa1!" + "x".repeat(60);
        assertThat(validator.isValid(exactly64, null)).isTrue();
        assertThat(validator.isValid(exactly64 + "x", null)).as("65 caracteres").isFalse();
    }
}
