package com.portfolix.api.auth;

import com.portfolix.api.auth.dto.AuthResponse;
import com.portfolix.api.auth.dto.GoogleLoginRequest;
import com.portfolix.api.auth.dto.LoginRequest;
import com.portfolix.api.auth.dto.RegisterRequest;
import com.portfolix.api.auth.oauth.ExternalAccount;
import com.portfolix.api.auth.oauth.GoogleIdTokenVerifier;
import com.portfolix.api.auth.oauth.OAuthAccountService;
import com.portfolix.api.auth.token.IssuedRefreshToken;
import com.portfolix.api.auth.token.RefreshTokenService;
import com.portfolix.api.common.exception.ForbiddenException;
import com.portfolix.api.security.JwtService;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import com.portfolix.api.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "Tenés que verificar tu correo antes de iniciar sesión. Revisá tu bandeja de entrada o pedí un link nuevo";

    private final UserService userService;
    private final CredentialsChecker credentialsChecker;
    private final EmailVerificationService emailVerificationService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final OAuthAccountService oauthAccountService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public AuthService(UserService userService, CredentialsChecker credentialsChecker,
                       EmailVerificationService emailVerificationService,
                       GoogleIdTokenVerifier googleIdTokenVerifier, OAuthAccountService oauthAccountService,
                       RefreshTokenService refreshTokenService, JwtService jwtService) {
        this.userService = userService;
        this.credentialsChecker = credentialsChecker;
        this.emailVerificationService = emailVerificationService;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.oauthAccountService = oauthAccountService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
    }

    /**
     * Crea la cuenta sin verificar y manda el link de verificación. Es una sola transacción:
     * si algo falla, no queda una cuenta creada sin su link.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = userService.createUser(request.fullName(), request.email(), request.password());
        emailVerificationService.sendVerification(user);
        return UserResponse.from(user);
    }

    /**
     * La verificación del mail se chequea después de la contraseña: así un 403 solo le confirma
     * que la cuenta existe a quien ya conoce la contraseña.
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = credentialsChecker.check(request.email(), request.password());
        if (!user.isEmailVerified()) {
            throw new ForbiddenException(EMAIL_NOT_VERIFIED_MESSAGE);
        }
        IssuedRefreshToken refreshToken = refreshTokenService.create(user, request.rememberMe());
        return result(refreshToken);
    }

    /**
     * "Continuar con Google": valida el ID token y abre una sesión igual que el login.
     * Si no había cuenta, la crea, y si había una con ese mail, la vincula (ver {@link OAuthAccountService}).
     * No pasa por el bloqueo por intentos: no hay contraseña que adivinar.
     */
    @Transactional
    public AuthResult loginWithGoogle(GoogleLoginRequest request) {
        ExternalAccount account = googleIdTokenVerifier.verify(request.idToken());
        User user = oauthAccountService.resolveUser(account);
        return result(refreshTokenService.create(user, request.rememberMe()));
    }

    public AuthResult refresh(String refreshTokenValue) {
        return result(refreshTokenService.rotate(refreshTokenValue));
    }

    public void logout(String refreshTokenValue) {
        refreshTokenService.revoke(refreshTokenValue);
    }

    private AuthResult result(IssuedRefreshToken refreshToken) {
        String accessToken = jwtService.issueAccessToken(refreshToken.userId(), refreshToken.sessionId());
        AuthResponse body = AuthResponse.bearer(accessToken, jwtService.accessTokenTtl().toSeconds());
        return new AuthResult(body, refreshToken);
    }
}
