package com.portfolix.api.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolix.api.common.DurationText;
import com.portfolix.api.common.exception.TooManyRequestsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Comparator;

/**
 * Aplica {@link RateLimited}: antes de ejecutar un endpoint anotado, descuenta un pedido de la IP del cliente.
 * Si no le quedan, responde 429: la excepción la atiende el GlobalExceptionHandler, porque los
 * interceptores corren dentro de Spring MVC (a diferencia de los filtros de Spring Security).
 * <p>
 * Usa el algoritmo "token bucket" (Bucket4j): por cada IP y política hay un balde de fichas que arranca
 * lleno y se recarga de a poco. A diferencia de contar "N pedidos por minuto de reloj", no deja pasar
 * el doble de pedidos justo en el cambio de minuto.
 * <p>
 * Como el bloqueo por intentos fallidos, vive en memoria: se reinicia con la app y cada instancia cuenta
 * por separado. La IP es la de la conexión ({@code getRemoteAddr}): detrás de un proxy sería la del proxy
 * para todos los clientes, así que al deployar hay que configurar {@code server.forward-headers-strategy}
 * para que Spring tome la del cliente del header {@code X-Forwarded-For}.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Tope de baldes a la vez, para que muchas IPs distintas no llenen la memoria. */
    private static final long MAX_TRACKED_CLIENTS = 100_000;

    private final RateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> buckets;

    @Autowired
    public RateLimitInterceptor(RateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_MILLISECONDS);
    }

    /** Para tests: un reloj controlable para los baldes. */
    RateLimitInterceptor(RateLimitProperties properties, TimeMeter timeMeter) {
        this.properties = properties;
        this.timeMeter = timeMeter;
        Duration longestPeriod = properties.limits().values().stream()
                .map(RateLimitProperties.Limit::period)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        this.buckets = Caffeine.newBuilder()
                // Un balde que no se usó durante todo su período ya está lleno otra vez: es igual a uno nuevo.
                .expireAfterAccess(longestPeriod)
                .maximumSize(MAX_TRACKED_CLIENTS)
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RateLimited rateLimited = method.getMethodAnnotation(RateLimited.class);
            if (rateLimited != null) {
                consume(rateLimited.value(), request.getRemoteAddr());
            }
        }
        return true;
    }

    /** Usa una ficha del balde de esa IP para esa política. Si no le quedan, 429 con el tiempo de espera. */
    void consume(RateLimitPolicy policy, String clientIp) {
        if (!properties.enabled()) {
            return;
        }
        Bucket bucket = buckets.get(policy + "|" + clientIp, key -> newBucket(properties.limits().get(policy)));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            Duration wait = Duration.ofNanos(probe.getNanosToWaitForRefill());
            throw new TooManyRequestsException(
                    "Hiciste demasiados pedidos seguidos. Probá de nuevo en " + DurationText.roundedUp(wait), wait);
        }
    }

    private Bucket newBucket(RateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.period()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }
}
