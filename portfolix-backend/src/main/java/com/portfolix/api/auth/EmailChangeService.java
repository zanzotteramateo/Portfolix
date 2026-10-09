package com.portfolix.api.auth;

import com.portfolix.api.auth.token.EmailToken;
import com.portfolix.api.auth.token.EmailTokenService;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.portfolix.api.auth.token.EmailTokenPurpose.EMAIL_CHANGE;

/**
 * Cambio de mail de una cuenta: se aplica recién cuando el usuario confirma el link que llega al mail
 * nuevo. Hasta entonces sigue entrando con el actual.
 */
@Service
public class EmailChangeService {

    static final String SAME_EMAIL_MESSAGE = "Es el mismo correo que ya tenés";
    private static final String NEW_EMAIL_FIELD = "newEmail";

    private final UserService userService;
    private final EmailTokenService emailTokenService;
    private final AuthMails authMails;

    public EmailChangeService(UserService userService, EmailTokenService emailTokenService, AuthMails authMails) {
        this.userService = userService;
        this.emailTokenService = emailTokenService;
        this.authMails = authMails;
    }

    /**
     * Guarda el mail nuevo en un token y le manda el link a esa dirección. Quien llama ya confirmó la
     * contraseña. Pedir otro cambio reemplaza al pendiente (ej.: para corregir un error de tipeo).
     */
    @Transactional
    public void request(User user, String newEmail) {
        String normalizedEmail = User.normalizeEmail(newEmail);
        if (normalizedEmail.equals(user.getEmail())) {
            throw new BusinessException(NEW_EMAIL_FIELD, SAME_EMAIL_MESSAGE);
        }
        userService.checkEmailAvailable(normalizedEmail);
        String token = emailTokenService.issueEmailChange(user, normalizedEmail);
        authMails.sendEmailChange(user, normalizedEmail, token);
    }

    /**
     * Aplica el cambio con el token del link. Abrir dos veces el link no es un error. Si el mail nuevo
     * se ocupó mientras tanto, 400 (y el link no queda gastado: se deshace todo).
     */
    @Transactional
    public void confirm(String tokenValue) {
        EmailToken token = emailTokenService.find(tokenValue, EMAIL_CHANGE);
        User user = token.getUser();
        if (token.isUsed() && user.getEmail().equals(token.getNewEmail())) {
            return;
        }
        emailTokenService.redeem(token);
        userService.changeEmail(user.getId(), token.getNewEmail());
    }
}
