package com.portfolix.api.auth;

import com.portfolix.api.auth.token.EmailToken;
import com.portfolix.api.auth.token.EmailTokenService;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.portfolix.api.auth.token.EmailTokenPurpose.EMAIL_VERIFICATION;

/**
 * Verificación del mail: la cuenta se crea sin verificar y se activa con el link que se manda por mail.
 * Hasta entonces no se puede iniciar sesión.
 */
@Service
public class EmailVerificationService {

    static final String ALREADY_VERIFIED_MESSAGE =
            "Tu cuenta ya está verificada. Iniciá sesión para cambiar el correo desde tu perfil";

    private final UserService userService;
    private final EmailTokenService emailTokenService;
    private final CredentialsChecker credentialsChecker;
    private final AuthMails authMails;

    public EmailVerificationService(UserService userService, EmailTokenService emailTokenService,
                                    CredentialsChecker credentialsChecker, AuthMails authMails) {
        this.userService = userService;
        this.emailTokenService = emailTokenService;
        this.credentialsChecker = credentialsChecker;
        this.authMails = authMails;
    }

    /** Manda el link para verificar el mail. El link del mail anterior, si había, deja de servir. */
    @Transactional
    public void sendVerification(User user) {
        String token = emailTokenService.issue(user, EMAIL_VERIFICATION);
        authMails.sendEmailVerification(user, token);
    }

    /**
     * Verifica la cuenta con el token del link. Abrir dos veces el mismo link no es un error:
     * pasa con un doble clic, o con el modo estricto de React, que en desarrollo ejecuta dos veces los efectos.
     */
    @Transactional
    public void verify(String tokenValue) {
        EmailToken token = emailTokenService.find(tokenValue, EMAIL_VERIFICATION);
        if (token.isUsed() && token.getUser().isEmailVerified()) {
            return;
        }
        emailTokenService.redeem(token);
        userService.markEmailVerified(token.getUser().getId());
    }

    /**
     * Reenvía el link. Responde lo mismo exista o no la cuenta (no revela qué mails están registrados),
     * así que si no corresponde mandar nada, simplemente no hace nada: el mail no tiene cuenta, la cuenta
     * ya está verificada, o se le mandó un link hace menos de un minuto (el anterior sigue sirviendo).
     */
    @Transactional
    public void resend(String email) {
        userService.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .filter(user -> !emailTokenService.issuedRecently(user.getId(), EMAIL_VERIFICATION))
                .ifPresent(this::sendVerification);
    }

    /**
     * Corrige el mail de una cuenta que todavía no se verificó (ej.: un error de tipeo al registrarse)
     * y manda el link al mail nuevo. Como no hay sesión, pide mail y contraseña actuales.
     */
    @Transactional
    public void changePendingEmail(String email, String password, String newEmail) {
        User user = credentialsChecker.check(email, password);
        if (user.isEmailVerified()) {
            throw new BusinessException(ALREADY_VERIFIED_MESSAGE);
        }
        userService.changeEmail(user.getId(), newEmail);
        sendVerification(user);
    }
}
