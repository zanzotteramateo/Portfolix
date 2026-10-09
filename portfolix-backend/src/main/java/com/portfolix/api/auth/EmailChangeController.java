package com.portfolix.api.auth;

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
 * Confirmación del cambio de mail. Es pública (no pide sesión): el link puede abrirse en otro dispositivo,
 * y el token ya prueba que quien lo usa recibió el mail. El pedido del cambio está en {@code /me/email-change}.
 */
@RestController
@RequestMapping("/api/v1/auth/email-change")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    public EmailChangeController(EmailChangeService emailChangeService) {
        this.emailChangeService = emailChangeService;
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited(RateLimitPolicy.TOKEN)
    public void confirm(@Valid @RequestBody TokenRequest request) {
        emailChangeService.confirm(request.token());
    }
}
