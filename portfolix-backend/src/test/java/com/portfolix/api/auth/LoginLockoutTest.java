package com.portfolix.api.auth;

import com.portfolix.api.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginLockoutTest {

    private static final String EMAIL = "juan@email.com";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T12:00:00Z"));
    private final LoginLockout lockout =
            new LoginLockout(new LoginLockoutProperties(5, Duration.ofMinutes(5)), clock);

    @Test
    void fiveFailuresInARow_lockTheEmailForFiveMinutes() {
        assertThat(failTimes(4)).isEqualTo(1);
        assertThat(lockout.remainingLock(EMAIL)).isEmpty();

        assertThat(lockout.recordFailure(EMAIL)).isZero();
        assertThat(lockout.remainingLock(EMAIL)).contains(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(3));
        assertThat(lockout.remainingLock(EMAIL)).contains(Duration.ofMinutes(2));

        clock.advance(Duration.ofMinutes(2));
        assertThat(lockout.remainingLock(EMAIL)).isEmpty();
        // Terminado el bloqueo, vuelve a tener los 5 intentos.
        assertThat(lockout.recordFailure(EMAIL)).isEqualTo(4);
    }

    @Test
    void failuresAreForgotten_afterTheLockDurationWithoutNewOnes() {
        failTimes(4);

        clock.advance(Duration.ofMinutes(5));

        assertThat(lockout.recordFailure(EMAIL)).isEqualTo(4);
    }

    @Test
    void failuresWhileLocked_doNotExtendTheLock() {
        failTimes(5);
        clock.advance(Duration.ofMinutes(1));

        assertThat(lockout.recordFailure(EMAIL)).isZero();

        assertThat(lockout.remainingLock(EMAIL)).contains(Duration.ofMinutes(4));
    }

    @Test
    void reset_forgetsTheFailuresAndUnlocks() {
        failTimes(5);

        lockout.reset(EMAIL);

        assertThat(lockout.remainingLock(EMAIL)).isEmpty();
        assertThat(lockout.recordFailure(EMAIL)).isEqualTo(4);
    }

    @Test
    void theCounterIsPerEmail_ignoringCaseAndSpaces() {
        failTimes(5);

        assertThat(lockout.remainingLock(" Juan@Email.COM ")).isPresent();
        assertThat(lockout.remainingLock("ana@email.com")).isEmpty();
    }

    /** Registra varios errores seguidos y devuelve los intentos que quedan después del último. */
    private int failTimes(int times) {
        int remaining = -1;
        for (int i = 0; i < times; i++) {
            remaining = lockout.recordFailure(EMAIL);
        }
        return remaining;
    }
}
