package com.portfolix.api.auth;

import com.portfolix.api.auth.dto.AuthResponse;
import com.portfolix.api.auth.dto.GoogleLoginRequest;
import com.portfolix.api.auth.dto.LoginRequest;
import com.portfolix.api.auth.dto.RegisterRequest;
import com.portfolix.api.common.exception.UnauthorizedException;
import com.portfolix.api.security.ratelimit.RateLimitPolicy;
import com.portfolix.api.security.ratelimit.RateLimited;
import com.portfolix.api.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    /** Manda el mail de verificación: comparte el límite por IP con los demás endpoints que mandan mails. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimited(RateLimitPolicy.EMAIL)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @RateLimited(RateLimitPolicy.LOGIN)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request));
    }

    /** "Continuar con Google": responde igual que el login (access token en el body, refresh token en la cookie). */
    @PostMapping("/oauth/google")
    @RateLimited(RateLimitPolicy.LOGIN)
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return withRefreshCookie(authService.loginWithGoogle(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = cookies.read(request)
                .orElseThrow(() -> new UnauthorizedException("Tu sesión expiró. Iniciá sesión de nuevo"));
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookies.read(request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(result.body());
    }
}
