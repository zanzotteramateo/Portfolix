package com.portfolix.api.security.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limita cuántas veces por IP se puede llamar a este endpoint, según la política indicada
 * (los límites están en {@code portfolix.rate-limit.limits}). Lo aplica {@link RateLimitInterceptor}.
 * <pre>{@code
 * @PostMapping("/login")
 * @RateLimited(RateLimitPolicy.LOGIN)
 * public ResponseEntity<AuthResponse> login(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    RateLimitPolicy value();
}
