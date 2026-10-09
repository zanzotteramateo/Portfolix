package com.portfolix.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj de la aplicación. Los services lo inyectan en lugar de llamar a {@code Instant.now()},
 * así los tests pueden fijar la hora y probar vencimientos sin esperar.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
