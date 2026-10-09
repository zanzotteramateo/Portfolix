package com.portfolix.api.common;

import java.time.Duration;

/**
 * Escribe una duración para mostrársela al usuario: "24 horas", "1 hora", "5 minutos".
 * Usa la unidad más grande que la expresa exacta (90 minutos → "90 minutos", no "1,5 horas").
 */
public final class DurationText {

    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3600;

    private DurationText() {
    }

    public static String of(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds > 0 && seconds % SECONDS_PER_HOUR == 0) {
            return withUnit(seconds / SECONDS_PER_HOUR, "hora", "horas");
        }
        if (seconds > 0 && seconds % SECONDS_PER_MINUTE == 0) {
            return withUnit(seconds / SECONDS_PER_MINUTE, "minuto", "minutos");
        }
        return withUnit(seconds, "segundo", "segundos");
    }

    /**
     * Para tiempos de espera ("probá de nuevo en 4 minutos"): redondea para arriba, a minutos enteros
     * si es un minuto o más y a segundos si es menos. Así nunca promete menos espera que la real.
     */
    public static String roundedUp(Duration duration) {
        long seconds = Math.max(1, (duration.toMillis() + 999) / 1000);
        if (seconds >= SECONDS_PER_MINUTE) {
            long minutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE;
            return withUnit(minutes, "minuto", "minutos");
        }
        return withUnit(seconds, "segundo", "segundos");
    }

    private static String withUnit(long amount, String singular, String plural) {
        return amount + " " + (amount == 1 ? singular : plural);
    }
}
