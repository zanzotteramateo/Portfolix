package com.portfolix.api.auth;

import com.portfolix.api.auth.token.EmailToken;
import com.portfolix.api.auth.token.EmailTokenService;
import com.portfolix.api.auth.token.RefreshTokenService;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.portfolix.api.auth.token.EmailTokenPurpose.PASSWORD_RESET;

/** "¿Olvidaste tu contraseña?": un link por mail para elegir una nueva. */
@Service
public class PasswordResetService {

    private final UserService userService;
    private final EmailTokenService emailTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginLockout loginLockout;
    private final AuthMails authMails;

    public PasswordResetService(UserService userService, EmailTokenService emailTokenService,
                                RefreshTokenService refreshTokenService, LoginLockout loginLockout,
                                AuthMails authMails) {
        this.userService = userService;
        this.emailTokenService = emailTokenService;
        this.refreshTokenService = refreshTokenService;
        this.loginLockout = loginLockout;
        this.authMails = authMails;
    }

    /**
     * Manda el link para restablecer la contraseña. Responde lo mismo exista o no la cuenta
     * (no revela qué mails están registrados), así que si no corresponde mandar nada, no hace nada:
     * el mail no tiene cuenta, o se le mandó un link hace menos de un minuto (el anterior sigue sirviendo).
     * Sirve también para una cuenta sin verificar: el link prueba que es el dueño del mail.
     */
    @Transactional
    public void requestReset(String email) {
        userService.findByEmail(email)
                .filter(user -> !emailTokenService.issuedRecently(user.getId(), PASSWORD_RESET))
                .ifPresent(user -> authMails.sendPasswordReset(user, emailTokenService.issue(user, PASSWORD_RESET)));
    }

    /**
     * Cambia la contraseña con el token del link. Además:
     * <ul>
     *   <li>cierra todas las sesiones abiertas (si alguien entró con la contraseña vieja, queda afuera);</li>
     *   <li>marca el mail como verificado: usar el link prueba que es el dueño;</li>
     *   <li>desbloquea el login, por si estaba bloqueado por intentos fallidos.</li>
     * </ul>
     * No inicia sesión: el front lleva al login. Es una sola transacción: si la contraseña nueva no sirve
     * (ej.: es igual a la actual), se deshace todo y el link se puede volver a usar.
     */
    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        EmailToken token = emailTokenService.find(tokenValue, PASSWORD_RESET);
        emailTokenService.redeem(token);
        User user = token.getUser();

        userService.changePassword(user.getId(), newPassword);
        userService.markEmailVerified(user.getId());
        refreshTokenService.revokeAll(user.getId());
        loginLockout.reset(user.getEmail());
    }
}
