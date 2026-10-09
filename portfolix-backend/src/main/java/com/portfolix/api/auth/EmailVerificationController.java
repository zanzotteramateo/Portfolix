package com.portfolix.api.auth;

import com.portfolix.api.auth.dto.ChangePendingEmailRequest;
import com.portfolix.api.auth.dto.EmailRequest;
import com.portfolix.api.auth.dto.TokenRequest;
import com.portfolix.api.security.ratelimit.RateLimitPolicy;
import com.portfolix.api.security.ratelimit.RateLimited;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los endpoints que mandan un mail responden 202 (Accepted): el pedido se aceptó,
 * pero el mail sale después, en segundo plano.
 */
@RestController
@RequestMapping("/api/v1/auth/verify-email")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    public EmailVerificationController(EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited(RateLimitPolicy.TOKEN)
    public void verify(@Valid @RequestBody TokenRequest request) {
        verificationService.verify(request.token());
    }

    /** Siempre 202, exista o no una cuenta pendiente con ese mail. */
    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimited(RateLimitPolicy.EMAIL)
    public void resend(@Valid @RequestBody EmailRequest request) {
        verificationService.resend(request.email());
    }

    @PostMapping("/change-email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimited(RateLimitPolicy.EMAIL)
    public void changePendingEmail(@Valid @RequestBody ChangePendingEmailRequest request) {
        verificationService.changePendingEmail(request.email(), request.password(), request.newEmail());
    }
}
