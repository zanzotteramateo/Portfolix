package com.portfolix.api.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Límites de pedidos por IP. Se leen de {@code portfolix.rate-limit}.
 *
 * @param enabled apagado no limita nada. Los tests lo apagan porque todos sus pedidos vienen de la misma IP
 * @param limits  el límite de cada política: tienen que estar todas
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        Map<RateLimitPolicy, @Valid Limit> limits
) {

    public RateLimitProperties {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            if (!limits.containsKey(policy)) {
                throw new IllegalArgumentException(
                        "Falta el límite de %s en portfolix.rate-limit.limits".formatted(policy));
            }
        }
    }

    /**
     * Un balde de {@code capacity} fichas que arranca lleno y se recarga de a poco, hasta llenarse
     * de nuevo en {@code period}. Cada pedido usa una ficha. Ej.: 10 por minuto permite 10 pedidos
     * seguidos y, después, uno cada 6 segundos.
     */
    public record Limit(@Min(1) int capacity, @NotNull Duration period) {
    }
}
