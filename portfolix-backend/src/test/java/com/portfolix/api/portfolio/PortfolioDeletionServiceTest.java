package com.portfolix.api.portfolio;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.transaction.TransactionService;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioDeletionServiceTest {

    private static final Long USER_ID = 1L;

    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final PortfolioDeletionService service =
            new PortfolioDeletionService(portfolioService, portfolioRepository, transactionService);

    private Portfolio portfolio3;
    private Portfolio portfolio7;

    @BeforeEach
    void setUp() {
        portfolio3 = portfolio(3L, "Ahorro");
        portfolio7 = portfolio(7L, "Jubilación");
        when(portfolioService.getOwnedForUpdate(USER_ID, 3L)).thenReturn(portfolio3);
        when(portfolioService.getOwnedForUpdate(USER_ID, 7L)).thenReturn(portfolio7);
    }

    @Test
    void bothOptions_areRejectedBeforeTouchingAnything() {
        assertThatThrownBy(() -> service.delete(USER_ID, 7L, 3L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PortfolioDeletionService.BOTH_OPTIONS_MESSAGE);
        verifyNoInteractions(portfolioService, portfolioRepository, transactionService);
    }

    @Test
    void move_locksBothPortfoliosInAscendingIdOrder_thenMovesAndDeletes() {
        service.delete(USER_ID, 7L, 3L, false);

        InOrder order = inOrder(portfolioService, transactionService, portfolioRepository);
        order.verify(portfolioService).getOwnedForUpdate(USER_ID, 3L); // el de id menor primero,
        order.verify(portfolioService).getOwnedForUpdate(USER_ID, 7L); // aunque sea el destino
        order.verify(transactionService).moveAll(portfolio7, portfolio3);
        order.verify(portfolioRepository).delete(portfolio7);
    }

    @Test
    void move_toTheSamePortfolio_isRejectedOnTheTargetField() {
        assertThatThrownBy(() -> service.delete(USER_ID, 7L, 7L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PortfolioDeletionService.SAME_TARGET_MESSAGE)
                .extracting("field").isEqualTo("moveTransactionsTo");
    }

    @Test
    void move_toAMissingTarget_saysItIsTheTarget() {
        when(portfolioService.getOwnedForUpdate(USER_ID, 9L))
                .thenThrow(new ResourceNotFoundException("Portafolio no encontrado"));

        assertThatThrownBy(() -> service.delete(USER_ID, 7L, 9L, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(PortfolioDeletionService.TARGET_NOT_FOUND_MESSAGE);
        verify(transactionService, never()).moveAll(any(), any());
        verify(portfolioRepository, never()).delete(any());
    }

    @Test
    void withTransactions_andNoChoice_isRejected() {
        when(transactionService.countByPortfolio(7L)).thenReturn(9L);

        assertThatThrownBy(() -> service.delete(USER_ID, 7L, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El portafolio tiene 9 transacciones: elegí moverlas a otro portafolio o eliminarlas");
        verify(portfolioRepository, never()).delete(any());
    }

    @Test
    void withOneTransaction_theMessageIsSingular() {
        when(transactionService.countByPortfolio(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(USER_ID, 7L, null, false))
                .hasMessage("El portafolio tiene 1 transacción: elegí moverla a otro portafolio o eliminarla");
    }

    @Test
    void deleteTransactions_deletesThePortfolioWithoutMovingAnything() {
        when(transactionService.countByPortfolio(7L)).thenReturn(9L);

        service.delete(USER_ID, 7L, null, true);

        verify(portfolioRepository).delete(portfolio7);
        verify(transactionService, never()).moveAll(any(), any());
    }

    @Test
    void emptyPortfolio_isDeletedWithoutChoosing() {
        when(transactionService.countByPortfolio(7L)).thenReturn(0L);

        service.delete(USER_ID, 7L, null, false);

        verify(portfolioRepository).delete(portfolio7);
    }

    private static Portfolio portfolio(Long id, String name) {
        Portfolio portfolio = new Portfolio(new User("juan@email.com", "hash", "Juan", Instant.now()), name);
        ReflectionTestUtils.setField(portfolio, "id", id);
        return portfolio;
    }
}
