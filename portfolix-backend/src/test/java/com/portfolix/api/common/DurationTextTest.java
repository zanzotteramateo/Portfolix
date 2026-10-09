package com.portfolix.api.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DurationTextTest {

    @ParameterizedTest
    @CsvSource({
            "PT24H, 24 horas",
            "PT1H, 1 hora",
            "PT90M, 90 minutos",
            "PT5M, 5 minutos",
            "PT1M, 1 minuto",
            "PT30S, 30 segundos",
            "PT1S, 1 segundo"
    })
    void of_usesTheLargestExactUnit(Duration duration, String expected) {
        assertThat(DurationText.of(duration)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "PT0S, 1 segundo",
            "PT4.5S, 5 segundos",
            "PT59S, 59 segundos",
            "PT60S, 1 minuto",
            "PT61S, 2 minutos",
            "PT4M10S, 5 minutos",
            "PT5M, 5 minutos"
    })
    void roundedUp_neverPromisesLessWaitThanTheReal(Duration duration, String expected) {
        assertThat(DurationText.roundedUp(duration)).isEqualTo(expected);
    }
}
