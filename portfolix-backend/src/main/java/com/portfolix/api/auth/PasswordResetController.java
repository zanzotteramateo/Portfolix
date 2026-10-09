package com.portfolix.api.auth;

import com.portfolix.api.auth.dto.EmailRequest;
import com.portfolix.api.auth.dto.ResetPasswordRequest;
import com.portfolix.api.security.ratelimit.RateLimitPolicy;
import com.portfolix.api.security.ratelimit.RateLimited;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** Siempre 202, exista o no una cuenta con ese mail. El mail sale después, en segundo plano. */
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimited(RateLimitPolicy.EMAIL)
    public void forgot(@Valid @RequestBody EmailRequest request) {
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited(RateLimitPolicy.TOKEN)
    public void reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }
}
