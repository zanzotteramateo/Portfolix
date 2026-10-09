package com.portfolix.api.auth.token;

import com.portfolix.api.common.exception.UnauthorizedException;
import com.portfolix.api.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.portfolix.api.auth.token.SecureTokens.hash;

@Service
public class RefreshTokenService {

    static final String INVALID_SESSION_MESSAGE = "Tu sesión expiró. Iniciá sesión de nuevo";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Abre una sesión nueva (login). */
    @Transactional
    public IssuedRefreshToken create(User user, boolean rememberMe) {
        return issue(user, rememberMe, UUID.randomUUID());
    }

    /**
     * Canjea un refresh token por uno nuevo (rotación): el anterior queda revocado.
     * Si llega un token que ya estaba revocado, alguien lo está reusando (probablemente robado):
     * se cierran todas las sesiones del usuario.
     * <p>
     * {@code noRollbackFor}: al detectar reuso se revocan las sesiones y después se lanza la excepción.
     * Sin esto, la excepción desharía la revocación.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedRefreshToken rotate(String value) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(hash(value))
                .orElseThrow(() -> new UnauthorizedException(INVALID_SESSION_MESSAGE));
        User user = current.getUser();

        if (current.isRevoked()) {
            log.warn("Reuso de refresh token detectado para el usuario {}: se cierran todas sus sesiones", user.getId());
            repository.revokeAllByUserId(user.getId(), now);
            throw new UnauthorizedException(INVALID_SESSION_MESSAGE);
        }
        if (current.isExpired(now)) {
            throw new UnauthorizedException(INVALID_SESSION_MESSAGE);
        }

        current.revoke(now);
        return issue(user, current.isRememberMe(), current.getSessionId());
    }

    /** Cierra la sesión de este dispositivo (logout). Si el token no existe, no hace nada. */
    @Transactional
    public void revoke(String value) {
        repository.findByTokenHash(hash(value)).ifPresent(token -> token.revoke(clock.instant()));
    }

    /** Cierra todas las sesiones del usuario (ej.: al restablecer la contraseña). */
    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllByUserId(userId, clock.instant());
    }

    /**
     * Cierra todas las sesiones del usuario menos la indicada: al cambiar la contraseña,
     * "cerramos la sesión en tus otros dispositivos". Si no se sabe cuál es la sesión actual
     * ({@code null}), las cierra todas.
     */
    @Transactional
    public void revokeOtherSessions(Long userId, UUID currentSessionId) {
        if (currentSessionId == null) {
            revokeAll(userId);
            return;
        }
        repository.revokeAllByUserIdExceptSession(userId, currentSessionId, clock.instant());
    }

    /** Emite un refresh token para una sesión: nueva al hacer login, la misma al rotar. */
    private IssuedRefreshToken issue(User user, boolean rememberMe, UUID sessionId) {
        String value = SecureTokens.generate();
        Duration ttl = properties.ttl(rememberMe);
        repository.save(new RefreshToken(user, hash(value), rememberMe, clock.instant().plus(ttl), sessionId));
        return new IssuedRefreshToken(value, user.getId(), sessionId, rememberMe, ttl);
    }
}
