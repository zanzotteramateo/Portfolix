package com.portfolix.api.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolix.api.user.User;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Bloqueo por intentos fallidos: después de {@code maxAttempts} contraseñas incorrectas seguidas
 * con un mismo mail, ese mail no puede iniciar sesión durante {@code lockDuration}, aunque después
 * ponga la contraseña correcta.
 * <p>
 * Se cuenta <b>por mail, exista o no la cuenta</b>: si solo se contaran las cuentas reales,
 * el aviso "te quedan 2 intentos" y el bloqueo revelarían qué mails están registrados.
 * <p>
 * Los contadores viven en memoria (Caffeine), no en la base: no hace falta una tabla ni borrar filas
 * viejas, porque cada contador se descarta solo. A cambio, se pierden si se reinicia la app, y con varias
 * instancias cada una cuenta por separado (con una sola alcanza; con varias habría que pasarlos a Redis).
 * <p>
 * Los tiempos se miden con el {@code Clock} de la app, así los tests pueden adelantar la hora.
 * Caffeine solo libera la memoria de los contadores que ya no cuentan.
 */
@Component
public class LoginLockout {

    /** Tope de mails seguidos a la vez, para que nadie llene la memoria probando mails inventados. */
    private static final long MAX_TRACKED_EMAILS = 100_000;

    private final LoginLockoutProperties properties;
    private final Clock clock;
    private final Cache<String, Attempts> attemptsByEmail;

    public LoginLockout(LoginLockoutProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.attemptsByEmail = Caffeine.newBuilder()
                // Pasado lockDuration desde el último error, el contador ya no cuenta (ver Attempts.isOver).
                .expireAfterWrite(properties.lockDuration())
                .maximumSize(MAX_TRACKED_EMAILS)
                .build();
    }

    /** Cuánto falta para que se desbloquee ese mail, o vacío si no está bloqueado. */
    public Optional<Duration> remainingLock(String email) {
        Instant now = clock.instant();
        Attempts attempts = current(key(email), now);
        if (attempts.lockedUntil() == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, attempts.lockedUntil()));
    }

    /**
     * Registra una contraseña incorrecta y devuelve cuántos intentos le quedan a ese mail:
     * 0 significa que está bloqueado. {@code compute} es atómico, así que dos errores
     * simultáneos no se pisan.
     */
    public int recordFailure(String email) {
        Instant now = clock.instant();
        Attempts updated = attemptsByEmail.asMap().compute(key(email), (key, previous) -> {
            Attempts attempts = previous == null || previous.isOver(now, properties.lockDuration())
                    ? Attempts.NONE
                    : previous;
            if (attempts.lockedUntil() != null) {
                return attempts; // ya estaba bloqueado: un error más no alarga el bloqueo
            }
            int failures = attempts.failures() + 1;
            Instant lockedUntil = failures >= properties.maxAttempts() ? now.plus(properties.lockDuration()) : null;
            return new Attempts(failures, now, lockedUntil);
        });
        return Math.max(0, properties.maxAttempts() - updated.failures());
    }

    /** Vuelve el contador a cero y desbloquea (login correcto o contraseña restablecida). */
    public void reset(String email) {
        attemptsByEmail.invalidate(key(email));
    }

    public Duration lockDuration() {
        return properties.lockDuration();
    }

    private Attempts current(String key, Instant now) {
        Attempts attempts = attemptsByEmail.getIfPresent(key);
        return attempts == null || attempts.isOver(now, properties.lockDuration()) ? Attempts.NONE : attempts;
    }

    /** Se cuenta sobre el mail normalizado: "Juan@Email.com" y "juan@email.com" comparten contador. */
    private static String key(String email) {
        return User.normalizeEmail(email);
    }

    /**
     * @param lastFailure cuándo fue el último error
     * @param lockedUntil hasta cuándo está bloqueado, o {@code null} si no lo está
     */
    private record Attempts(int failures, Instant lastFailure, Instant lockedUntil) {

        static final Attempts NONE = new Attempts(0, null, null);

        /** Ya no cuenta: terminó el bloqueo, o pasó {@code lockDuration} sin errores nuevos. */
        boolean isOver(Instant now, Duration lockDuration) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            return lastFailure != null && !now.isBefore(lastFailure.plus(lockDuration));
        }
    }
}
