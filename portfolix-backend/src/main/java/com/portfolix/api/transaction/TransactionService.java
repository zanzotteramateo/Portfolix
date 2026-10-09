package com.portfolix.api.transaction;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ConflictException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.portfolio.PortfolioService;
import com.portfolix.api.transaction.dto.TransactionPageResponse;
import com.portfolix.api.transaction.dto.TransactionRequest;
import com.portfolix.api.transaction.dto.TransactionResponse;
import com.portfolix.api.transaction.dto.TransactionSummary;
import com.portfolix.api.user.preference.DecimalSeparator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.portfolix.api.transaction.TransactionSpecifications.belongsToUser;
import static com.portfolix.api.transaction.TransactionSpecifications.forAsset;
import static com.portfolix.api.transaction.TransactionSpecifications.inPortfolio;
import static com.portfolix.api.transaction.TransactionSpecifications.ofType;
import static com.portfolix.api.transaction.TransactionSpecifications.tradedOnOrAfter;
import static com.portfolix.api.transaction.TransactionSpecifications.tradedOnOrBefore;

@Service
public class TransactionService {

    static final String NOT_FOUND_MESSAGE = "Transacción no encontrada";
    static final String CHANGED_MESSAGE = "La transacción cambió mientras tanto. Volvé a abrirla e intentá de nuevo";
    static final String INACTIVE_ASSET_MESSAGE = "Este activo ya no está disponible para comprar";
    static final String INVALID_RANGE_MESSAGE = "La fecha desde no puede ser posterior a la fecha hasta";
    static final String DELETE_UNCOVERS_SALE_MESSAGE = "No se puede eliminar: %s quedaría sin tenencia suficiente";
    static final String UPDATE_UNCOVERS_SALE_MESSAGE = "Este cambio deja sin tenencia suficiente a %s";

    /** La más reciente primero; dentro del mismo día, la última cargada primero. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("tradeDate"), Sort.Order.desc("id"));
    /** Orden cronológico: el mismo en que se validan las ventas y se calculan las posiciones. */
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("tradeDate"), Sort.Order.asc("id"));
    /** El mismo orden que {@link #OLDEST_FIRST}, para ordenar en memoria. */
    private static final Comparator<Transaction> CHRONOLOGICAL =
            Comparator.comparing(Transaction::getTradeDate).thenComparing(Transaction::getId);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransactionRepository transactionRepository;
    private final PortfolioService portfolioService;
    private final AssetService assetService;
    private final CurrencyConverter converter;

    public TransactionService(TransactionRepository transactionRepository, PortfolioService portfolioService,
                              AssetService assetService, CurrencyConverter converter) {
        this.transactionRepository = transactionRepository;
        this.portfolioService = portfolioService;
        this.assetService = assetService;
        this.converter = converter;
    }

    /**
     * Registra una compra o una venta. La fecha no futura y los formatos ya los validó el DTO.
     * <p>
     * Toda operación que escribe transacciones bloquea antes su portafolio (SELECT ... FOR UPDATE):
     * <ul>
     *   <li>dos ventas simultáneas se validan de a una: la segunda ve la tenencia ya descontada;</li>
     *   <li>una compra simultánea al borrado del portafolio espera y responde 404, en vez de fallar
     *   con un 500 porque la base rechaza una transacción de un portafolio que ya no existe.</li>
     * </ul>
     */
    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest request) {
        Portfolio portfolio = portfolioService.getOwnedForUpdate(userId, request.portfolioId());
        Asset asset = assetService.getBySymbol(request.assetSymbol());

        if (request.type() == TransactionType.SELL) {
            validateSale(portfolio, asset, request);
        } else if (!asset.isActive()) {
            // Un activo dado de baja no se puede comprar, pero sí vender lo que ya se tiene.
            throw new BusinessException("assetSymbol", INACTIVE_ASSET_MESSAGE);
        }

        Transaction transaction = new Transaction(portfolio, asset, request.type(), request.quantity(),
                request.price(), request.tradeDate(), normalizeNotes(request.notes()));
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long transactionId) {
        return TransactionResponse.from(transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE)));
    }

    /**
     * Reemplaza todos los datos de una transacción (el body es el mismo formulario del alta), con las reglas del alta:
     * la moneda se copia del activo y una venta no puede superar la tenencia en su fecha.
     * <p>
     * Si cambian el activo o el portafolio, el cambio afecta dos historiales: el que pierde la transacción
     * y el que la recibe. En ninguno de los dos puede quedar una venta sin tenencia suficiente.
     * <p>
     * La transacción conserva su orden de alta: si pasa a un día con otras transacciones,
     * queda antes de las que se cargaron después que ella (el mismo orden de las posiciones).
     */
    @Transactional
    public TransactionResponse update(Long userId, Long transactionId, TransactionRequest request) {
        LockedTransaction locked = lockAndLoad(userId, transactionId, request.portfolioId());
        Transaction transaction = locked.transaction();
        Portfolio portfolio = locked.targetPortfolio();
        Asset asset = assetService.getBySymbol(request.assetSymbol());

        // Un activo dado de baja no se puede comprar. Una compra que ya existía de ese activo se puede corregir
        // (cantidad, precio, fecha, notas, portafolio), pero ninguna edición puede crear una compra nueva de él.
        boolean wasBuyOfThatAsset = transaction.getType() == TransactionType.BUY
                && transaction.getAsset().getId().equals(asset.getId());
        if (request.type() == TransactionType.BUY && !asset.isActive() && !wasBuyOfThatAsset) {
            throw new BusinessException("assetSymbol", INACTIVE_ASSET_MESSAGE);
        }

        // Los historiales se leen antes de modificar la entidad: si se consultara con la entidad ya modificada,
        // Hibernate la guardaría en la base antes de la consulta (para que la consulta la vea actualizada).
        boolean sameHistory = transaction.getPortfolio().getId().equals(portfolio.getId())
                && transaction.getAsset().getId().equals(asset.getId());
        List<Transaction> previousHistory = history(transaction.getPortfolio(), transaction.getAsset());
        List<Transaction> others = sameHistory ? previousHistory : history(portfolio, asset);
        previousHistory.removeIf(tx -> tx.getId().equals(transaction.getId()));
        // Ahora "others" es el historial donde va a quedar la transacción, sin ella.

        transaction.update(portfolio, asset, request.type(), request.quantity(), request.price(),
                request.tradeDate(), normalizeNotes(request.notes()));

        if (transaction.getType() == TransactionType.SELL) {
            // Su lugar en el historial: después de todas las que van antes por fecha y orden de alta.
            int position = (int) others.stream().filter(tx -> CHRONOLOGICAL.compare(tx, transaction) < 0).count();
            checkSaleQuantity(transaction.getQuantity(), SellableQuantity.at(others, position),
                    SellableQuantity.current(others));
        }
        if (!sameHistory) {
            rejectUncoveredSale(previousHistory, UPDATE_UNCOVERS_SALE_MESSAGE);
        }
        List<Transaction> newHistory = new ArrayList<>(others);
        newHistory.add(transaction);
        newHistory.sort(CHRONOLOGICAL);
        rejectUncoveredSale(newHistory, UPDATE_UNCOVERS_SALE_MESSAGE);

        // No hace falta save(): la entidad la administra JPA y sus cambios se guardan solos al confirmar
        // la transacción ("dirty checking"). Si algo de arriba falla, el rollback descarta el cambio.
        return TransactionResponse.from(transaction);
    }

    /**
     * Elimina una transacción. Borrar una compra puede dejar sin tenencia a una venta posterior
     * del mismo activo en el portafolio: en ese caso se rechaza y el mensaje dice cuál.
     */
    @Transactional
    public void delete(Long userId, Long transactionId) {
        Transaction transaction = lockAndLoad(userId, transactionId, null).transaction();
        // Borrar una venta nunca deja descubierta a otra: la tenencia de ahí en adelante solo sube.
        if (transaction.getType() == TransactionType.BUY) {
            List<Transaction> remaining = history(transaction.getPortfolio(), transaction.getAsset());
            remaining.removeIf(tx -> tx.getId().equals(transaction.getId()));
            rejectUncoveredSale(remaining, DELETE_UNCOVERS_SALE_MESSAGE);
        }
        transactionRepository.delete(transaction);
    }

    /**
     * Historial del usuario, filtrado y paginado, con el resumen de totales.
     * El resumen aplica los mismos filtros salvo el tipo: así las tarjetas de totales
     * no cambian al alternar entre Compras y Ventas.
     *
     * @param currency moneda del total convertido del resumen
     */
    @Transactional(readOnly = true)
    public TransactionPageResponse list(Long userId, TransactionFilter filter, Currency currency, int page, int size) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessException("from", INVALID_RANGE_MESSAGE);
        }

        List<Specification<Transaction>> conditions = new ArrayList<>();
        conditions.add(belongsToUser(userId)); // siempre: nunca se listan transacciones de otro usuario
        if (filter.portfolioId() != null) {
            portfolioService.getOwned(userId, filter.portfolioId()); // 404 si no existe o es de otro usuario
            conditions.add(inPortfolio(filter.portfolioId()));
        }
        if (filter.assetSymbol() != null && !filter.assetSymbol().isBlank()) {
            conditions.add(forAsset(assetService.getBySymbol(filter.assetSymbol()).getId()));
        }
        if (filter.from() != null) {
            conditions.add(tradedOnOrAfter(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add(tradedOnOrBefore(filter.to()));
        }

        Specification<Transaction> summaryScope = Specification.allOf(conditions);
        Specification<Transaction> listScope = filter.type() == null
                ? summaryScope
                : summaryScope.and(ofType(filter.type()));

        Page<TransactionResponse> transactions = transactionRepository
                .findAll(listScope, PageRequest.of(page, size, NEWEST_FIRST))
                .map(TransactionResponse::from);
        // Cada fila del resumen es de un solo día: se convierte con el dólar de esa fecha.
        TransactionSummary summary = TransactionSummary.from(transactionRepository.summarize(summaryScope), currency,
                row -> row.amount().multiply(converter.factorOn(row.tradeDate(), row.currency(), currency)));
        return TransactionPageResponse.of(transactions, summary);
    }

    /**
     * Transacciones del usuario en orden cronológico, con portafolio y activo ya cargados.
     * Filtros opcionales ({@code null} = todos). Las usa el módulo holding para calcular posiciones.
     * Un portafolio ajeno no devuelve nada (el filtro por usuario lo excluye); validarlo con 404 es tarea de quien llama.
     */
    @Transactional(readOnly = true)
    public List<Transaction> findForPositions(Long userId, Long portfolioId, Long assetId) {
        List<Specification<Transaction>> conditions = new ArrayList<>();
        conditions.add(belongsToUser(userId));
        if (portfolioId != null) {
            conditions.add(inPortfolio(portfolioId));
        }
        if (assetId != null) {
            conditions.add(forAsset(assetId));
        }
        return transactionRepository.findAll(Specification.allOf(conditions), OLDEST_FIRST);
    }

    @Transactional(readOnly = true)
    public long countByPortfolio(Long portfolioId) {
        return transactionRepository.countByPortfolioId(portfolioId);
    }

    /** Todo el historial del usuario en CSV, en orden cronológico (ver {@link TransactionCsv}). */
    @Transactional(readOnly = true)
    public String exportCsv(Long userId, DecimalSeparator separator) {
        return TransactionCsv.write(findForPositions(userId, null, null), separator);
    }

    /**
     * Copia todas las transacciones de un portafolio a otro, en el mismo orden (así se mantiene el orden
     * dentro de cada día). Quien llama bloqueó el portafolio original, por eso exige una transacción abierta.
     * No hace falta revalidar ventas: es el mismo historial, que ya era válido.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int copyAll(Portfolio source, Portfolio target) {
        List<Transaction> copies = transactionRepository.findAllByPortfolioIdOrderByTradeDateAscIdAsc(source.getId())
                .stream()
                .map(original -> new Transaction(target, original.getAsset(), original.getType(),
                        original.getQuantity(), original.getPrice(), original.getTradeDate(), original.getNotes()))
                .toList();
        transactionRepository.saveAll(copies);
        return copies.size();
    }

    @Transactional(readOnly = true)
    public long countByUser(Long userId) {
        return transactionRepository.countByUserId(userId);
    }

    /** En cuántos activos distintos operó el usuario (para el resumen antes de eliminar la cuenta). */
    @Transactional(readOnly = true)
    public long countAssetsByUser(Long userId) {
        return transactionRepository.countDistinctAssetsByUserId(userId);
    }

    /**
     * Pasa todas las transacciones de un portafolio a otro. Quien llama tiene que haber bloqueado
     * los dos portafolios, por eso exige una transacción abierta (MANDATORY).
     * No hace falta revalidar ventas: si la tenencia de cada portafolio nunca fue negativa,
     * la suma de las dos tampoco puede serlo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int moveAll(Portfolio source, Portfolio target) {
        return transactionRepository.moveAll(source, target);
    }

    /** La transacción a editar o borrar y el portafolio donde va a quedar, los dos ya bloqueados. */
    private record LockedTransaction(Transaction transaction, Portfolio targetPortfolio) {
    }

    /**
     * Bloquea el portafolio de una transacción del usuario (y el de destino, si una edición la pasa a otro)
     * y recién después la carga.
     * <p>
     * El orden importa. Para saber qué portafolio bloquear hay que leer la transacción, pero si se la cargara
     * entera antes del bloqueo se podría validar con datos viejos: otro pedido la pudo cambiar mientras
     * esperábamos el turno. Por eso primero se lee solo el id de su portafolio. Si en ese intervalo otro pedido
     * la borró, responde 404; si la movió a otro portafolio, 409 (el historial bloqueado ya no es el suyo).
     *
     * @param targetPortfolioId el portafolio donde va a quedar; {@code null} = el mismo
     */
    private LockedTransaction lockAndLoad(Long userId, Long transactionId, Long targetPortfolioId) {
        Long currentId = transactionRepository.findPortfolioIdByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        Long targetId = targetPortfolioId == null ? currentId : targetPortfolioId;

        // Dos portafolios se bloquean siempre en el mismo orden (id menor primero), como al eliminar un portafolio
        // moviendo sus transacciones: si dos pedidos cruzados bloquearan en distinto orden, se esperarían entre sí.
        Portfolio target = targetId < currentId ? portfolioService.getOwnedForUpdate(userId, targetId) : null;
        Portfolio current = lockCurrentPortfolio(userId, currentId, transactionId);
        if (target == null) {
            target = targetId.equals(currentId) ? current : portfolioService.getOwnedForUpdate(userId, targetId);
        }

        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        if (!transaction.getPortfolio().getId().equals(currentId)) {
            throw new ConflictException(CHANGED_MESSAGE);
        }
        return new LockedTransaction(transaction, target);
    }

    /**
     * Si eliminaron el portafolio mientras esperábamos el bloqueo, la transacción
     * se borró con él (404) o se movió a otro portafolio (409).
     */
    private Portfolio lockCurrentPortfolio(Long userId, Long portfolioId, Long transactionId) {
        try {
            return portfolioService.getOwnedForUpdate(userId, portfolioId);
        } catch (ResourceNotFoundException ex) {
            throw transactionRepository.findPortfolioIdByIdAndUserId(transactionId, userId).isPresent()
                    ? new ConflictException(CHANGED_MESSAGE)
                    : new ResourceNotFoundException(NOT_FOUND_MESSAGE);
        }
    }

    /** Historial de un activo en un portafolio, en orden cronológico, en una lista que se puede modificar. */
    private List<Transaction> history(Portfolio portfolio, Asset asset) {
        return new ArrayList<>(transactionRepository
                .findAllByPortfolioIdAndAssetIdOrderByTradeDateAscIdAsc(portfolio.getId(), asset.getId()));
    }

    private void validateSale(Portfolio portfolio, Asset asset, TransactionRequest request) {
        List<Transaction> history = history(portfolio, asset);
        checkSaleQuantity(request.quantity(), SellableQuantity.at(history, request.tradeDate()),
                SellableQuantity.current(history));
    }

    /**
     * @param available lo que se puede vender en la fecha de la venta
     * @param holding   la tenencia actual, sin contar la venta
     */
    private static void checkSaleQuantity(BigDecimal quantity, BigDecimal available, BigDecimal holding) {
        if (quantity.compareTo(available) <= 0) {
            return;
        }
        // Si lo disponible es menor que la tenencia actual, es por la fecha: se aclara en el mensaje.
        boolean limitedByDate = available.compareTo(holding) < 0;
        String message = limitedByDate
                ? "Supera tu tenencia a esa fecha (%s)".formatted(format(available))
                : "Supera tu tenencia (%s)".formatted(format(available));
        throw new BusinessException("quantity", message);
    }

    /** Rechaza el cambio si, con el historial como quedaría, alguna venta se queda sin tenencia suficiente. */
    private static void rejectUncoveredSale(List<Transaction> history, String message) {
        Optional<Transaction> uncovered = SellableQuantity.firstUncoveredSale(history);
        if (uncovered.isPresent()) {
            throw new BusinessException(message.formatted(describeSale(uncovered.get())));
        }
    }

    /** "la venta de 8 YPFD del 20/09/2026 en Jubilación" */
    private static String describeSale(Transaction sale) {
        return "la venta de %s %s del %s en %s".formatted(format(sale.getQuantity()), sale.getAsset().getSymbol(),
                DAY_FORMAT.format(sale.getTradeDate()), sale.getPortfolio().getName());
    }

    /** Formato argentino para el mensaje: 0.3421 → "0,3421". */
    private static String format(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }
}
