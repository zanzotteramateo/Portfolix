package com.portfolix.api.auth;

import com.portfolix.api.auth.token.EmailToken;
import com.portfolix.api.auth.token.EmailTokenService;
import com.portfolix.api.auth.token.RefreshTokenService;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.portfolix.api.auth.token.EmailTokenPurpose.PASSWORD_RESET;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los flujos completos (con la base, los mails y las sesiones) se prueban en PasswordResetIntegrationTest. */
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final UserService userService = mock(UserService.class);
    private final EmailTokenService emailTokenService = mock(EmailTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final LoginLockout loginLockout = mock(LoginLockout.class);
    private final AuthMails authMails = mock(AuthMails.class);

    private final PasswordResetService service = new PasswordResetService(
            userService, emailTokenService, refreshTokenService, loginLockout, authMails);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("juan@email.com", "hash", "Juan Pérez", NOW);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void requestReset_sendsTheLink() {
        when(userService.findByEmail("juan@email.com")).thenReturn(Optional.of(user));
        when(emailTokenService.issue(user, PASSWORD_RESET)).thenReturn("token");

        service.requestReset("juan@email.com");

        verify(authMails).sendPasswordReset(user, "token");
    }

    @Test
    void requestReset_withinTheCooldown_sendsNothing() {
        when(userService.findByEmail("juan@email.com")).thenReturn(Optional.of(user));
        when(emailTokenService.issuedRecently(1L, PASSWORD_RESET)).thenReturn(true);

        service.requestReset("juan@email.com");

        verify(emailTokenService, never()).issue(any(), any());
        verify(authMails, never()).sendPasswordReset(any(), any());
    }

    @Test
    void requestReset_forUnknownEmail_sendsNothing() {
        when(userService.findByEmail("nadie@email.com")).thenReturn(Optional.empty());

        service.requestReset("nadie@email.com");

        verify(authMails, never()).sendPasswordReset(any(), any());
    }

    @Test
    void resetPassword_changesIt_verifiesTheEmail_closesSessions_andUnlocksTheLogin() {
        EmailToken token = new EmailToken(user, PASSWORD_RESET, "hash", NOW, NOW.plus(Duration.ofHours(1)));
        when(emailTokenService.find("valor", PASSWORD_RESET)).thenReturn(token);

        service.resetPassword("valor", "NuevaClave2026!");

        var order = inOrder(emailTokenService, userService);
        order.verify(emailTokenService).redeem(token);
        order.verify(userService).changePassword(1L, "NuevaClave2026!");
        verify(userService).markEmailVerified(1L);
        verify(refreshTokenService).revokeAll(1L);
        verify(loginLockout).reset("juan@email.com");
    }
}
