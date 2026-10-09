package com.portfolix.api.auth;

import com.portfolix.api.MutableClock;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.TooManyRequestsException;
import com.portfolix.api.common.exception.UnauthorizedException;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El conteo de intentos se prueba con un LoginLockout real (en memoria, con un reloj controlable). */
class CredentialsCheckerTest {

    private static final String PASSWORD = "Inversion2026!";

    private final UserService userService = mock(UserService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final LoginLockout loginLockout = new LoginLockout(
            new LoginLockoutProperties(5, Duration.ofMinutes(5)), new MutableClock(Instant.now()));

    private CredentialsChecker checker;
    private User user;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}dummy");
        checker = new CredentialsChecker(userService, passwordEncoder, loginLockout);
        user = new User("juan@email.com", "{bcrypt}real-hash", "Juan Pérez", Instant.now());
        when(userService.findByEmail("juan@email.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "{bcrypt}real-hash")).thenReturn(true);
    }

    @Test
    void check_withValidCredentials_returnsTheUser() {
        assertThat(checker.check("juan@email.com", PASSWORD)).isSameAs(user);
    }

    @Test
    void check_withWrongPassword_failsWithGenericMessage() {
        assertThatThrownBy(() -> checker.check("juan@email.com", "Incorrecta1!"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(CredentialsChecker.BAD_CREDENTIALS_MESSAGE);
    }

    @Test
    void check_withUnknownEmail_failsWithSameMessageAndStillChecksAPassword() {
        when(userService.findByEmail("nadie@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checker.check("nadie@email.com", PASSWORD))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(CredentialsChecker.BAD_CREDENTIALS_MESSAGE);
        // Compara contra el hash falso para que la respuesta tarde lo mismo que con un mail existente.
        verify(passwordEncoder).matches(eq(PASSWORD), eq("{bcrypt}dummy"));
    }

    @Test
    void check_onAnAccountWithoutPassword_alwaysFails() {
        User googleOnly = new User("google@email.com", null, "Juan Pérez", Instant.now());
        when(userService.findByEmail("google@email.com")).thenReturn(Optional.of(googleOnly));
        // Aunque la contraseña coincidiera con la del hash falso, una cuenta sin contraseña no entra.
        when(passwordEncoder.matches(anyString(), eq("{bcrypt}dummy"))).thenReturn(true);

        assertThatThrownBy(() -> checker.check("google@email.com", "lo-que-sea"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(CredentialsChecker.BAD_CREDENTIALS_MESSAGE);
    }

    @Test
    void check_warnsWhenFewAttemptsRemain_andLocksOnTheFifthFailure() {
        assertThat(failedAttemptMessage()).isEqualTo("Correo o contraseña incorrectos");
        assertThat(failedAttemptMessage()).isEqualTo("Correo o contraseña incorrectos");
        assertThat(failedAttemptMessage()).isEqualTo(
                "Correo o contraseña incorrectos. Te quedan 2 intentos antes de que bloqueemos el acceso por 5 minutos");
        assertThat(failedAttemptMessage()).isEqualTo(
                "Correo o contraseña incorrectos. Te queda 1 intento antes de que bloqueemos el acceso por 5 minutos");

        Throwable locked = catchThrowable(() -> checker.check("juan@email.com", "Incorrecta1!"));
        assertThat(locked).isInstanceOf(TooManyRequestsException.class)
                .hasMessage("Por seguridad, bloqueamos el acceso por demasiados intentos fallidos. "
                        + "Probá de nuevo en 5 minutos o restablecé tu contraseña");
        assertThat(((TooManyRequestsException) locked).getRetryAfter()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void check_whileLocked_rejectsEvenTheRightPassword_withoutCheckingIt() {
        failTimes(5);
        clearInvocations(passwordEncoder);

        assertThatThrownBy(() -> checker.check("juan@email.com", PASSWORD))
                .isInstanceOf(TooManyRequestsException.class);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void check_withTheRightPassword_resetsTheFailures() {
        failTimes(4);

        checker.check("juan@email.com", PASSWORD);

        assertThat(failedAttemptMessage()).isEqualTo(CredentialsChecker.BAD_CREDENTIALS_MESSAGE);
    }

    @Test
    void check_withUnknownEmail_alsoLocks_soTheLockDoesNotRevealWhichEmailsExist() {
        when(userService.findByEmail("nadie@email.com")).thenReturn(Optional.empty());
        for (int i = 0; i < 4; i++) {
            catchThrowable(() -> checker.check("nadie@email.com", PASSWORD));
        }

        assertThatThrownBy(() -> checker.check("nadie@email.com", PASSWORD))
                .isInstanceOf(TooManyRequestsException.class);
    }

    // ---- confirm: la contraseña de un usuario con sesión ----

    @Test
    void confirm_withTheRightPassword_passes() {
        checker.confirm(user, PASSWORD);
    }

    @Test
    void confirm_withWrongPassword_failsOnTheCurrentPasswordField_notWith401() {
        assertThatThrownBy(() -> checker.confirm(user, "Incorrecta1!"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CredentialsChecker.WRONG_CURRENT_PASSWORD_MESSAGE)
                .extracting("field").isEqualTo("currentPassword");
    }

    @Test
    void confirm_sharesTheLockWithTheLogin() {
        for (int i = 0; i < 4; i++) {
            catchThrowable(() -> checker.confirm(user, "Incorrecta1!"));
        }

        assertThatThrownBy(() -> checker.confirm(user, "Incorrecta1!"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> checker.check("juan@email.com", PASSWORD))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void confirm_onAnAccountWithoutPassword_asksToCreateOne() {
        User googleOnly = new User("google@email.com", null, "Juan Pérez", Instant.now());

        assertThatThrownBy(() -> checker.confirm(googleOnly, "lo-que-sea"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CredentialsChecker.NO_PASSWORD_MESSAGE);
    }

    private String failedAttemptMessage() {
        Throwable error = catchThrowable(() -> checker.check("juan@email.com", "Incorrecta1!"));
        assertThat(error).isInstanceOf(UnauthorizedException.class);
        return error.getMessage();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            catchThrowable(() -> checker.check("juan@email.com", "Incorrecta1!"));
        }
    }
}
