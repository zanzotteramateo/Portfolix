package com.portfolix.api.auth;

import com.portfolix.api.common.DurationText;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.TooManyRequestsException;
import com.portfolix.api.common.exception.UnauthorizedException;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifica contraseñas. Todo endpoint que recibe una contraseña pasa por acá, así todos responden
 * igual ante un error y comparten el bloqueo por intentos fallidos ({@link LoginLockout}):
 * <ul>
 *   <li>{@link #check}: sin sesión (login, cambio de mail de una cuenta sin verificar);</li>
 *   <li>{@link #confirm}: con sesión, antes de una operación sensible (cambiar la contraseña o el mail,
 *       eliminar la cuenta).</li>
 * </ul>
 */
@Component
public class CredentialsChecker {

    static final String BAD_CREDENTIALS_MESSAGE = "Correo o contraseña incorrectos";
    static final String WRONG_CURRENT_PASSWORD_MESSAGE = "La contraseña actual no es correcta";
    static final String NO_PASSWORD_MESSAGE = "Tu cuenta no tiene contraseña porque entrás con Google. "
            + "Creá una con \"¿Olvidaste tu contraseña?\" y volvé a intentarlo";
    /** Desde cuántos intentos restantes se avisa del bloqueo (el Figma lo muestra con 2). */
    private static final int WARN_WHEN_REMAINING = 2;
    private static final String CURRENT_PASSWORD_FIELD = "currentPassword";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final LoginLockout loginLockout;
    /**
     * Hash de una contraseña aleatoria, distinta en cada arranque. Si el mail no existe (o la cuenta no tiene
     * contraseña), igual se compara contra este hash para que la respuesta tarde lo mismo: si no, midiendo
     * el tiempo se sabría qué mails tienen cuenta. Que sea aleatoria es una defensa extra: nadie la conoce.
     */
    private final String dummyPasswordHash;

    public CredentialsChecker(UserService userService, PasswordEncoder passwordEncoder, LoginLockout loginLockout) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.loginLockout = loginLockout;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Devuelve el usuario si mail y contraseña coinciden. Si no, 401 con un mensaje que no dice
     * cuál de los dos falló (no revela si el mail tiene cuenta), o 429 si el mail quedó bloqueado.
     * Mientras el mail está bloqueado, ni se mira la contraseña.
     */
    public User check(String email, String password) {
        throwIfLocked(email);

        Optional<User> user = userService.findByEmail(email);
        Optional<String> passwordHash = user.map(User::getPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash.orElse(dummyPasswordHash));
        // Sin contraseña propia (mail sin cuenta, o cuenta que entra solo con Google) nunca hay coincidencia,
        // aunque la contraseña coincida con la del hash falso.
        if (passwordHash.isEmpty() || !passwordMatches) {
            int remainingAttempts = recordFailure(email);
            throw new UnauthorizedException(withLockWarning(BAD_CREDENTIALS_MESSAGE, remainingAttempts));
        }

        loginLockout.reset(email);
        return user.get();
    }

    /**
     * Confirma la contraseña de un usuario que ya tiene sesión. Comparte el bloqueo con el login: si no,
     * quien robara una sesión podría probar contraseñas acá sin límite.
     * <p>
     * El error es un 400 en el campo {@code currentPassword}, no un 401: el usuario sigue autenticado,
     * y un 401 haría que el front crea que se venció la sesión. Una cuenta sin contraseña (solo Google)
     * primero tiene que crearse una.
     */
    public void confirm(User user, String password) {
        if (user.getPasswordHash() == null) {
            throw new BusinessException(CURRENT_PASSWORD_FIELD, NO_PASSWORD_MESSAGE);
        }
        throwIfLocked(user.getEmail());

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int remainingAttempts = recordFailure(user.getEmail());
            throw new BusinessException(CURRENT_PASSWORD_FIELD,
                    withLockWarning(WRONG_CURRENT_PASSWORD_MESSAGE, remainingAttempts));
        }

        loginLockout.reset(user.getEmail());
    }

    private void throwIfLocked(String email) {
        loginLockout.remainingLock(email).ifPresent(remaining -> {
            throw locked(remaining);
        });
    }

    /** Registra el error y devuelve los intentos que quedan; si este error bloqueó el mail, 429. */
    private int recordFailure(String email) {
        int remainingAttempts = loginLockout.recordFailure(email);
        if (remainingAttempts == 0) {
            throw locked(loginLockout.lockDuration());
        }
        return remainingAttempts;
    }

    /** El mensaje de error y, si quedan pocos intentos, el aviso del bloqueo. */
    private String withLockWarning(String message, int remainingAttempts) {
        if (remainingAttempts > WARN_WHEN_REMAINING) {
            return message;
        }
        String attempts = remainingAttempts == 1
                ? "Te queda 1 intento"
                : "Te quedan %d intentos".formatted(remainingAttempts);
        return "%s. %s antes de que bloqueemos el acceso por %s".formatted(
                message, attempts, DurationText.of(loginLockout.lockDuration()));
    }

    private static TooManyRequestsException locked(Duration remaining) {
        String message = ("Por seguridad, bloqueamos el acceso por demasiados intentos fallidos. "
                + "Probá de nuevo en %s o restablecé tu contraseña").formatted(DurationText.roundedUp(remaining));
        return new TooManyRequestsException(message, remaining);
    }
}
