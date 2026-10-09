package com.portfolix.api.auth;

import com.portfolix.api.auth.token.EmailToken;
import com.portfolix.api.auth.token.EmailTokenService;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.portfolix.api.auth.token.EmailTokenPurpose.EMAIL_VERIFICATION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los flujos completos (con la base y los mails) se prueban en EmailVerificationIntegrationTest. */
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final UserService userService = mock(UserService.class);
    private final EmailTokenService emailTokenService = mock(EmailTokenService.class);
    private final CredentialsChecker credentialsChecker = mock(CredentialsChecker.class);
    private final AuthMails authMails = mock(AuthMails.class);

    private final EmailVerificationService service =
            new EmailVerificationService(userService, emailTokenService, credentialsChecker, authMails);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("juan@email.com", "hash", "Juan Pérez", NOW);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void verify_withValidToken_redeemsItAndVerifiesTheAccount() {
        EmailToken token = token();
        when(emailTokenService.find("valor", EMAIL_VERIFICATION)).thenReturn(token);

        service.verify("valor");

        verify(emailTokenService).redeem(token);
        verify(userService).markEmailVerified(1L);
    }

    @Test
    void verify_withAlreadyUsedTokenOfVerifiedAccount_doesNothing() {
        EmailToken token = token();
        token.markUsed(NOW);
        user.setEmailVerified(true);
        when(emailTokenService.find("valor", EMAIL_VERIFICATION)).thenReturn(token);

        service.verify("valor");

        verify(emailTokenService, never()).redeem(any());
    }

    @Test
    void resend_afterTheCooldown_sendsANewLink() {
        when(userService.findByEmail("juan@email.com")).thenReturn(Optional.of(user));
        when(emailTokenService.issuedRecently(1L, EMAIL_VERIFICATION)).thenReturn(false);
        when(emailTokenService.issue(user, EMAIL_VERIFICATION)).thenReturn("nuevo");

        service.resend("juan@email.com");

        verify(authMails).sendEmailVerification(user, "nuevo");
    }

    @Test
    void resend_withinTheCooldown_sendsNothing() {
        when(userService.findByEmail("juan@email.com")).thenReturn(Optional.of(user));
        when(emailTokenService.issuedRecently(1L, EMAIL_VERIFICATION)).thenReturn(true);

        service.resend("juan@email.com");

        verify(emailTokenService, never()).issue(any(), any());
        verify(authMails, never()).sendEmailVerification(any(), any());
    }

    private EmailToken token() {
        return new EmailToken(user, EMAIL_VERIFICATION, "hash", NOW, NOW.plus(Duration.ofHours(24)));
    }
}
