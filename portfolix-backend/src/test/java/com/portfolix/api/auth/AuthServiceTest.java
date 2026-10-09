package com.portfolix.api.auth;

import com.portfolix.api.auth.dto.GoogleLoginRequest;
import com.portfolix.api.auth.dto.LoginRequest;
import com.portfolix.api.auth.dto.RegisterRequest;
import com.portfolix.api.auth.oauth.ExternalAccount;
import com.portfolix.api.auth.oauth.GoogleIdTokenVerifier;
import com.portfolix.api.auth.oauth.IdentityProvider;
import com.portfolix.api.auth.oauth.OAuthAccountService;
import com.portfolix.api.auth.token.IssuedRefreshToken;
import com.portfolix.api.auth.token.RefreshTokenService;
import com.portfolix.api.common.exception.ForbiddenException;
import com.portfolix.api.security.JwtService;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El chequeo de mail y contraseña se prueba en CredentialsCheckerTest. */
class AuthServiceTest {

    private static final UUID SESSION = UUID.randomUUID();

    private final UserService userService = mock(UserService.class);
    private final CredentialsChecker credentialsChecker = mock(CredentialsChecker.class);
    private final EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
    private final GoogleIdTokenVerifier googleIdTokenVerifier = mock(GoogleIdTokenVerifier.class);
    private final OAuthAccountService oauthAccountService = mock(OAuthAccountService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final JwtService jwtService = mock(JwtService.class);

    private final AuthService authService = new AuthService(userService, credentialsChecker,
            emailVerificationService, googleIdTokenVerifier, oauthAccountService, refreshTokenService, jwtService);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("juan@email.com", "{bcrypt}real-hash", "Juan Pérez", Instant.now());
        ReflectionTestUtils.setField(user, "id", 7L);
        when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    }

    @Test
    void register_createsTheAccountAndSendsTheVerificationLink() {
        when(userService.createUser("Juan Pérez", "juan@email.com", "Inversion2026!")).thenReturn(user);

        var response = authService.register(
                new RegisterRequest("Juan Pérez", "juan@email.com", "Inversion2026!", true));

        assertThat(response.email()).isEqualTo("juan@email.com");
        verify(emailVerificationService).sendVerification(user);
    }

    @Test
    void login_withValidCredentialsOnVerifiedAccount_issuesTokens() {
        user.setEmailVerified(true);
        when(credentialsChecker.check("juan@email.com", "Inversion2026!")).thenReturn(user);
        when(refreshTokenService.create(user, true))
                .thenReturn(new IssuedRefreshToken("refresh", 7L, SESSION, true, Duration.ofDays(30)));
        when(jwtService.issueAccessToken(7L, SESSION)).thenReturn("access");

        AuthResult result = authService.login(new LoginRequest("juan@email.com", "Inversion2026!", true));

        assertThat(result.body().accessToken()).isEqualTo("access");
        assertThat(result.body().tokenType()).isEqualTo("Bearer");
        assertThat(result.body().expiresIn()).isEqualTo(900);
        assertThat(result.refreshToken().value()).isEqualTo("refresh");
    }

    @Test
    void loginWithGoogle_opensASessionForTheAccountOfThatGoogleUser() {
        ExternalAccount account = new ExternalAccount(IdentityProvider.GOOGLE, "sub-123", "juan@email.com", "Juan Pérez");
        when(googleIdTokenVerifier.verify("id-token")).thenReturn(account);
        when(oauthAccountService.resolveUser(account)).thenReturn(user);
        when(refreshTokenService.create(user, true))
                .thenReturn(new IssuedRefreshToken("refresh", 7L, SESSION, true, Duration.ofDays(30)));
        when(jwtService.issueAccessToken(7L, SESSION)).thenReturn("access");

        AuthResult result = authService.loginWithGoogle(new GoogleLoginRequest("id-token", true));

        assertThat(result.body().accessToken()).isEqualTo("access");
        assertThat(result.refreshToken().rememberMe()).isTrue();
    }

    @Test
    void login_onUnverifiedAccount_isForbiddenAndOpensNoSession() {
        when(credentialsChecker.check("juan@email.com", "Inversion2026!")).thenReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("juan@email.com", "Inversion2026!", false)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(AuthService.EMAIL_NOT_VERIFIED_MESSAGE);
        verify(refreshTokenService, never()).create(any(), anyBoolean());
    }
}
