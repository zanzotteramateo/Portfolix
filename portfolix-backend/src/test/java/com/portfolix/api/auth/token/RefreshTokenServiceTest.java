package com.portfolix.api.auth.token;

import com.portfolix.api.common.exception.UnauthorizedException;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final UUID SESSION = UUID.randomUUID();

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenProperties properties =
            new RefreshTokenProperties(Duration.ofDays(30), Duration.ofDays(1), "portfolix_refresh", true);
    private final RefreshTokenService service =
            new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("juan@email.com", "hash", "Juan Pérez", NOW);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_usesTtlAccordingToRememberMe() {
        assertThat(service.create(user, true).ttl()).isEqualTo(Duration.ofDays(30));
        assertThat(service.create(user, false).ttl()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void create_storesOnlyTheHash() {
        IssuedRefreshToken issued = service.create(user, false);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(token ->
                token.getTokenHash().equals(SecureTokens.hash(issued.value()))
                        && !token.getTokenHash().equals(issued.value())));
    }

    @Test
    void create_opensANewSessionEachTime() {
        assertThat(service.create(user, false).sessionId()).isNotEqualTo(service.create(user, false).sessionId());
    }

    @Test
    void rotate_revokesTheCurrentToken_andKeepsRememberMeAndTheSession() {
        RefreshToken current = new RefreshToken(user, "h", true, NOW.plus(Duration.ofDays(10)), SESSION);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

        IssuedRefreshToken rotated = service.rotate("old-token");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotated.rememberMe()).isTrue();
        assertThat(rotated.userId()).isEqualTo(1L);
        assertThat(rotated.sessionId()).isEqualTo(SESSION);
    }

    @Test
    void revokeOtherSessions_keepsTheCurrentOne() {
        service.revokeOtherSessions(1L, SESSION);

        verify(repository).revokeAllByUserIdExceptSession(1L, SESSION, NOW);
        verify(repository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void revokeOtherSessions_withoutKnowingTheCurrentOne_revokesThemAll() {
        service.revokeOtherSessions(1L, null);

        verify(repository).revokeAllByUserId(1L, NOW);
    }

    @Test
    void rotate_withRevokedToken_revokesAllUserSessions() {
        RefreshToken reused = new RefreshToken(user, "h", false, NOW.plus(Duration.ofDays(1)), SESSION);
        reused.revoke(NOW.minusSeconds(60));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> service.rotate("stolen-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeAllByUserId(1L, NOW);
    }

    @Test
    void rotate_withExpiredToken_isRejected() {
        RefreshToken expired = new RefreshToken(user, "h", false, NOW, SESSION);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).revokeAllByUserId(any(), any());
    }

    @Test
    void rotate_withUnknownToken_isRejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("made-up"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
