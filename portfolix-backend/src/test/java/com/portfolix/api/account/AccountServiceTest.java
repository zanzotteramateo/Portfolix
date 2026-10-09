package com.portfolix.api.account;

import com.portfolix.api.auth.CredentialsChecker;
import com.portfolix.api.auth.EmailChangeService;
import com.portfolix.api.auth.token.RefreshTokenService;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.portfolio.PortfolioService;
import com.portfolix.api.transaction.TransactionService;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los flujos completos (sesiones reales, mails, borrado en cascada) se prueban en AccountIntegrationTest. */
class AccountServiceTest {

    private final UserService userService = mock(UserService.class);
    private final CredentialsChecker credentialsChecker = mock(CredentialsChecker.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final EmailChangeService emailChangeService = mock(EmailChangeService.class);
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final TransactionService transactionService = mock(TransactionService.class);

    private final AccountService service = new AccountService(userService, credentialsChecker,
            refreshTokenService, emailChangeService, portfolioService, transactionService);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("juan@email.com", "hash", "Juan Pérez", Instant.now());
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userService.getById(1L)).thenReturn(user);
    }

    @Test
    void changePassword_confirmsTheCurrentOne_changesIt_andClosesTheOtherSessions() {
        UUID session = UUID.randomUUID();

        service.changePassword(1L, session.toString(), "Actual2026!", "Nueva2026!");

        InOrder order = inOrder(credentialsChecker, userService, refreshTokenService);
        order.verify(credentialsChecker).confirm(user, "Actual2026!");
        order.verify(userService).changePassword(1L, "Nueva2026!");
        order.verify(refreshTokenService).revokeOtherSessions(1L, session);
    }

    @Test
    void changePassword_withATokenWithoutValidSession_closesAllSessions() {
        service.changePassword(1L, null, "Actual2026!", "Nueva2026!");
        service.changePassword(1L, "no-es-un-uuid", "Actual2026!", "Nueva2026!");

        verify(refreshTokenService, times(2)).revokeOtherSessions(1L, null);
    }

    @Test
    void changePassword_withWrongCurrentPassword_changesNothing() {
        doThrow(new BusinessException("currentPassword", "La contraseña actual no es correcta"))
                .when(credentialsChecker).confirm(user, "Mala2026!");

        assertThatThrownBy(() -> service.changePassword(1L, null, "Mala2026!", "Nueva2026!"))
                .isInstanceOf(BusinessException.class);
        verify(userService, never()).changePassword(any(), any());
        verify(refreshTokenService, never()).revokeOtherSessions(any(), any());
    }

    @Test
    void requestEmailChange_confirmsThePasswordFirst() {
        service.requestEmailChange(1L, "Actual2026!", "nuevo@email.com");

        InOrder order = inOrder(credentialsChecker, emailChangeService);
        order.verify(credentialsChecker).confirm(user, "Actual2026!");
        order.verify(emailChangeService).request(user, "nuevo@email.com");
    }

    @Test
    void deletionSummary_countsWhatWouldBeDeleted() {
        when(portfolioService.countOwned(1L)).thenReturn(2L);
        when(transactionService.countAssetsByUser(1L)).thenReturn(3L);
        when(transactionService.countByUser(1L)).thenReturn(4L);

        var summary = service.deletionSummary(1L);

        assertThat(summary.portfolios()).isEqualTo(2);
        assertThat(summary.assets()).isEqualTo(3);
        assertThat(summary.transactions()).isEqualTo(4);
        assertThat(summary.email()).isEqualTo("juan@email.com");
    }

    @Test
    void deleteAccount_withWrongPassword_deletesNothing() {
        doThrow(new BusinessException("currentPassword", "La contraseña actual no es correcta"))
                .when(credentialsChecker).confirm(user, "Mala2026!");

        assertThatThrownBy(() -> service.deleteAccount(1L, "Mala2026!")).isInstanceOf(BusinessException.class);
        verify(userService, never()).deleteUser(any());
    }
}
