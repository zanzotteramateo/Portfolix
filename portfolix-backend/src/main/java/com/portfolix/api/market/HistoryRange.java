package com.portfolix.api.market;

import com.portfolix.api.common.exception.BusinessException;

import java.time.Duration;
import java.util.Arrays;

/**
 * Rangos de los gráficos de tendencia. {@code param} es cómo se piden en la URL ({@code ?range=7d}).
 */
public enum HistoryRange {
    DAY("24h", Duration.ofDays(1)),
    WEEK("7d", Duration.ofDays(7));

    private final String param;
    private final Duration duration;

    HistoryRange(String param, Duration duration) {
        this.param = param;
        this.duration = duration;
    }

    public String param() {
        return param;
    }

    public Duration duration() {
        return duration;
    }

    public static HistoryRange fromParam(String param) {
        return Arrays.stream(values())
                .filter(range -> range.param.equalsIgnoreCase(param))
                .findFirst()
                .orElseThrow(() -> new BusinessException("range", "El rango tiene que ser 24h o 7d"));
    }
}
