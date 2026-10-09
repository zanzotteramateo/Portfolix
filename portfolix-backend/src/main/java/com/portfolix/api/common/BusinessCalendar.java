package com.portfolix.api.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Hoy" según la zona horaria del negocio (Argentina), no la del servidor (UTC).
 * A las 22 h en Buenos Aires el servidor ya está en el día siguiente: sin esto,
 * la validación de "fecha no futura" dependería de dónde corre la app.
 */
@Component
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, @Value("${portfolix.business-time-zone}") ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    public ZoneId zone() {
        return zone;
    }
}
