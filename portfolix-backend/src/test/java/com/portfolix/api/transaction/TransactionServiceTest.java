package com.portfolix.api.transaction;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ConflictException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.portfolio.PortfolioService;
import com.portfolix.api.transaction.dto.TransactionRequest;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PORTFOLIO_ID = 10L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 25);

    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final AssetService assetService = mock(AssetService.class);
    private final TransactionService service = new TransactionService(repository, portfolioService, assetService,
            mock(CurrencyConverter.class));

    private Portfolio portfolio;
    private Asset btc;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio(new User("juan@email.com", "hash", "Juan", Instant.now()), "Jubilación");
        ReflectionTestUtils.setField(portfolio, "id", PORTFOLIO_ID);
        btc = new Asset("BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD);
        ReflectionTestUtils.setField(btc, "id", 100L);

        when(portfolioService.getOwnedForUpdate(USER_ID, PORTFOLIO_ID)).thenReturn(portfolio);
        when(assetService.getBySymbol("BTC")).thenReturn(btc);
        when(repository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void buy_copiesCurrencyFromAssetAndNormalizesNotes() {
        var response = service.create(USER_ID, request(TransactionType.BUY, "0.05", "   "));

        assertThat(response.currency()).isEqualTo(Currency.USD);
        assertThat(response.total()).isEqualByComparingTo("3620"); // 0.05 × 72400
        assertThat(response.notes()).isNull();
        verify(portfolioService).getOwnedForUpdate(USER_ID, PORTFOLIO_ID); // las compras también bloquean
    }

    @Test
    void buy_ofInactiveAsset_isRejected() {
        btc.setActive(false);

        assertThatThrownBy(() -> service.create(USER_ID, request(TransactionType.BUY, "1", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransactionService.INACTIVE_ASSET_MESSAGE)
                .extracting("field").isEqualTo("assetSymbol");
    }

    @Test
    void sell_ofInactiveAsset_isAllowedIfThereIsHolding() {
        btc.setActive(false);
        holding(new Transaction(portfolio, btc, TransactionType.BUY, new BigDecimal("2"), BigDecimal.ONE, DAY.minusDays(1), null));

        var response = service.create(USER_ID, request(TransactionType.SELL, "1", null));

        assertThat(response.type()).isEqualTo(TransactionType.SELL);
        verify(portfolioService).getOwnedForUpdate(USER_ID, PORTFOLIO_ID); // las ventas bloquean
    }

    @Test
    void sell_exceedingHolding_isRejectedOnQuantityField() {
        holding(new Transaction(portfolio, btc, TransactionType.BUY, new BigDecimal("0.3421"), BigDecimal.ONE, DAY.minusDays(1), null));

        assertThatThrownBy(() -> service.create(USER_ID, request(TransactionType.SELL, "0.5", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Supera tu tenencia (0,3421)")
                .extracting("field").isEqualTo("quantity");
        verify(repository, never()).save(any());
    }

    @Test
    void backdatedSell_explainsThatTheLimitIsTheDate() {
        holding(new Transaction(portfolio, btc, TransactionType.BUY, new BigDecimal("1"), BigDecimal.ONE, DAY.minusDays(1), null),
                new Transaction(portfolio, btc, TransactionType.BUY, new BigDecimal("5"), BigDecimal.ONE, DAY.plusDays(1), null));

        assertThatThrownBy(() -> service.create(USER_ID, request(TransactionType.SELL, "3", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Supera tu tenencia a esa fecha (1)");
    }

    // ---- Borrado: bloqueo, carreras y ventas descubiertas ----

    @Test
    void delete_locksThePortfolioBeforeLoadingTheTransaction() {
        Transaction sale = stored(withId(500L, tx(portfolio, TransactionType.SELL, "1", DAY)));

        service.delete(USER_ID, 500L);

        InOrder order = inOrder(repository, portfolioService);
        order.verify(repository).findPortfolioIdByIdAndUserId(500L, USER_ID);
        order.verify(portfolioService).getOwnedForUpdate(USER_ID, PORTFOLIO_ID);
        order.verify(repository).findByIdAndUserId(500L, USER_ID);
        order.verify(repository).delete(sale);
    }

    @Test
    void delete_ofBuyThatCoversALaterSale_namesThatSale() {
        Transaction firstBuy = withId(1L, tx(portfolio, TransactionType.BUY, "5", DAY.minusDays(10)));
        Transaction secondBuy = stored(withId(2L, tx(portfolio, TransactionType.BUY, "5", DAY.minusDays(5))));
        Transaction sale = withId(3L, tx(portfolio, TransactionType.SELL, "8", DAY));
        holding(firstBuy, secondBuy, sale);

        assertThatThrownBy(() -> service.delete(USER_ID, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No se puede eliminar: la venta de 8 BTC del 25/09/2026 en Jubilación "
                        + "quedaría sin tenencia suficiente")
                .extracting("field").isNull();
        verify(repository, never()).delete(any(Transaction.class));
    }

    @Test
    void transactionMovedWhileWaitingForTheLock_returns409() {
        Portfolio trading = withId(20L, new Portfolio(portfolio.getUser(), "Trading"));
        Transaction moved = withId(500L, tx(trading, TransactionType.BUY, "1", DAY));
        when(repository.findPortfolioIdByIdAndUserId(500L, USER_ID)).thenReturn(Optional.of(PORTFOLIO_ID));
        when(repository.findByIdAndUserId(500L, USER_ID)).thenReturn(Optional.of(moved));

        assertThatThrownBy(() -> service.delete(USER_ID, 500L))
                .isInstanceOf(ConflictException.class)
                .hasMessage(TransactionService.CHANGED_MESSAGE);
        verify(repository, never()).delete(any(Transaction.class));
    }

    @Test
    void portfolioDeletedWhileWaiting_withTheTransaction_returns404() {
        when(repository.findPortfolioIdByIdAndUserId(500L, USER_ID))
                .thenReturn(Optional.of(PORTFOLIO_ID), Optional.empty());
        when(portfolioService.getOwnedForUpdate(USER_ID, PORTFOLIO_ID))
                .thenThrow(new ResourceNotFoundException("Portafolio no encontrado"));

        assertThatThrownBy(() -> service.delete(USER_ID, 500L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(TransactionService.NOT_FOUND_MESSAGE);
    }

    @Test
    void portfolioDeletedWhileWaiting_movingTheTransaction_returns409() {
        when(repository.findPortfolioIdByIdAndUserId(500L, USER_ID))
                .thenReturn(Optional.of(PORTFOLIO_ID), Optional.of(20L));
        when(portfolioService.getOwnedForUpdate(USER_ID, PORTFOLIO_ID))
                .thenThrow(new ResourceNotFoundException("Portafolio no encontrado"));

        assertThatThrownBy(() -> service.delete(USER_ID, 500L)).isInstanceOf(ConflictException.class);
    }

    // ---- Edición ----

    @Test
    void update_toAnotherPortfolio_locksBothInAscendingIdOrder() {
        Portfolio ahorro = withId(5L, new Portfolio(portfolio.getUser(), "Ahorro"));
        when(portfolioService.getOwnedForUpdate(USER_ID, 5L)).thenReturn(ahorro);
        stored(withId(500L, tx(portfolio, TransactionType.BUY, "1", DAY)));

        var response = service.update(USER_ID, 500L, updateRequest(5L, "BTC", TransactionType.BUY, "1", DAY));

        assertThat(response.portfolio().name()).isEqualTo("Ahorro");
        // El destino (5) antes que el actual (10), aunque el actual se lea primero: siempre el id menor primero.
        InOrder order = inOrder(portfolioService, repository);
        order.verify(repository).findPortfolioIdByIdAndUserId(500L, USER_ID);
        order.verify(portfolioService).getOwnedForUpdate(USER_ID, 5L);
        order.verify(portfolioService).getOwnedForUpdate(USER_ID, PORTFOLIO_ID);
        order.verify(repository).findByIdAndUserId(500L, USER_ID);
    }

    @Test
    void editedSale_keepsItsPlaceWithinTheDay() {
        // Compra 10 el día 1 (id 1), venta (id 2) y compra 5 el día 10 (id 3). Si la venta pasa al día 10,
        // va antes que la compra de ese día, que se cargó después: dispone de 10, no de 15.
        Transaction sale = withId(2L, tx(portfolio, TransactionType.SELL, "1", DAY.minusDays(20)));
        holding(withId(1L, tx(portfolio, TransactionType.BUY, "10", DAY.minusDays(24))), stored(sale),
                withId(3L, tx(portfolio, TransactionType.BUY, "5", DAY)));

        var request = updateRequest(PORTFOLIO_ID, "BTC", TransactionType.SELL, "12", DAY);

        assertThatThrownBy(() -> service.update(USER_ID, 2L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Supera tu tenencia a esa fecha (10)")
                .extracting("field").isEqualTo("quantity");
    }

    @Test
    void inactiveAsset_anExistingBuyCanBeCorrected() {
        btc.setActive(false);
        stored(withId(500L, tx(portfolio, TransactionType.BUY, "1", DAY)));

        var response = service.update(USER_ID, 500L,
                updateRequest(PORTFOLIO_ID, "BTC", TransactionType.BUY, "3", DAY));

        assertThat(response.quantity()).isEqualByComparingTo("3");
    }

    @Test
    void inactiveAsset_aSaleCannotBecomeABuy() {
        btc.setActive(false);
        stored(withId(500L, tx(portfolio, TransactionType.SELL, "1", DAY)));

        var request = updateRequest(PORTFOLIO_ID, "BTC", TransactionType.BUY, "1", DAY);

        assertThatThrownBy(() -> service.update(USER_ID, 500L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransactionService.INACTIVE_ASSET_MESSAGE)
                .extracting("field").isEqualTo("assetSymbol");
    }

    @Test
    void inactiveAsset_aBuyOfAnotherAssetCannotBecomeOneOfIt() {
        Asset luna = withId(200L, new Asset("LUNA", "Terra", AssetType.CRYPTO, Currency.USD));
        luna.setActive(false);
        when(assetService.getBySymbol("LUNA")).thenReturn(luna);
        stored(withId(500L, tx(portfolio, TransactionType.BUY, "1", DAY)));

        var request = updateRequest(PORTFOLIO_ID, "LUNA", TransactionType.BUY, "1", DAY);

        assertThatThrownBy(() -> service.update(USER_ID, 500L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(TransactionService.INACTIVE_ASSET_MESSAGE);
    }

    @Test
    void inactiveAsset_aSaleCanBeEdited() {
        btc.setActive(false);
        holding(withId(1L, tx(portfolio, TransactionType.BUY, "2", DAY.minusDays(5))),
                stored(withId(2L, tx(portfolio, TransactionType.SELL, "1", DAY))));

        var response = service.update(USER_ID, 2L,
                updateRequest(PORTFOLIO_ID, "BTC", TransactionType.SELL, "2", DAY));

        assertThat(response.quantity()).isEqualByComparingTo("2");
    }

    private static TransactionRequest updateRequest(Long portfolioId, String symbol, TransactionType type,
                                                    String quantity, LocalDate date) {
        return new TransactionRequest(portfolioId, symbol, type, new BigDecimal(quantity), BigDecimal.ONE, date, null);
    }

    private Transaction tx(Portfolio owner, TransactionType type, String quantity, LocalDate date) {
        return new Transaction(owner, btc, type, new BigDecimal(quantity), BigDecimal.ONE, date, null);
    }

    private static <T> T withId(Long id, T entity) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    /** Simula que la transacción está guardada: la encuentran las consultas por id del usuario. */
    private Transaction stored(Transaction transaction) {
        when(repository.findPortfolioIdByIdAndUserId(transaction.getId(), USER_ID))
                .thenReturn(Optional.of(transaction.getPortfolio().getId()));
        when(repository.findByIdAndUserId(transaction.getId(), USER_ID)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    private void holding(Transaction... transactions) {
        when(repository.findAllByPortfolioIdAndAssetIdOrderByTradeDateAscIdAsc(PORTFOLIO_ID, 100L))
                .thenReturn(List.of(transactions));
    }

    private static TransactionRequest request(TransactionType type, String quantity, String notes) {
        return new TransactionRequest(PORTFOLIO_ID, "BTC", type, new BigDecimal(quantity),
                new BigDecimal("72400"), DAY, notes);
    }
}
