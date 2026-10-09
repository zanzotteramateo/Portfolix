package com.portfolix.api.portfolio;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elimina portafolios decidiendo qué pasa con sus transacciones.
 * <p>
 * Está separado de PortfolioService para evitar una dependencia circular: TransactionService
 * ya usa PortfolioService (para validar el portafolio de cada transacción), así que PortfolioService
 * no puede usar TransactionService. Si lo hiciera, cada uno necesitaría al otro para crearse
 * y Spring no arrancaría.
 */
@Service
public class PortfolioDeletionService {

    static final String BOTH_OPTIONS_MESSAGE = "Elegí mover las transacciones o eliminarlas, no las dos cosas";
    static final String SAME_TARGET_MESSAGE = "Elegí un portafolio distinto al que vas a eliminar";
    static final String TARGET_NOT_FOUND_MESSAGE = "Portafolio de destino no encontrado";

    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final TransactionService transactionService;

    public PortfolioDeletionService(PortfolioService portfolioService, PortfolioRepository portfolioRepository,
                                    TransactionService transactionService) {
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.transactionService = transactionService;
    }

    /**
     * Elimina un portafolio del usuario. Si tiene transacciones hay que elegir:
     * moverlas a otro portafolio ({@code moveTransactionsTo}) o borrarlas ({@code deleteTransactions}).
     * Sin elegir, solo se puede eliminar un portafolio vacío: un olvido del front nunca borra historial.
     */
    @Transactional
    public void delete(Long userId, Long portfolioId, Long moveTransactionsTo, boolean deleteTransactions) {
        if (moveTransactionsTo != null && deleteTransactions) {
            throw new BusinessException(BOTH_OPTIONS_MESSAGE);
        }
        if (moveTransactionsTo != null) {
            moveTransactionsAndDelete(userId, portfolioId, moveTransactionsTo);
            return;
        }

        Portfolio portfolio = portfolioService.getOwnedForUpdate(userId, portfolioId);
        long transactions = transactionService.countByPortfolio(portfolio.getId());
        if (transactions > 0 && !deleteTransactions) {
            throw new BusinessException(pendingTransactionsMessage(transactions));
        }
        // ON DELETE CASCADE: la base borra sus transacciones junto con el portafolio.
        portfolioRepository.delete(portfolio);
    }

    private void moveTransactionsAndDelete(Long userId, Long sourceId, Long targetId) {
        if (sourceId.equals(targetId)) {
            throw new BusinessException("moveTransactionsTo", SAME_TARGET_MESSAGE);
        }
        // Se bloquean los dos portafolios, siempre en el mismo orden (id menor primero).
        // Si dos borrados cruzados (A→B y B→A) bloquearan en distinto orden, cada uno
        // quedaría esperando al otro para siempre (deadlock).
        Portfolio source;
        Portfolio target;
        if (sourceId < targetId) {
            source = portfolioService.getOwnedForUpdate(userId, sourceId);
            target = lockTarget(userId, targetId);
        } else {
            target = lockTarget(userId, targetId);
            source = portfolioService.getOwnedForUpdate(userId, sourceId);
        }
        transactionService.moveAll(source, target);
        portfolioRepository.delete(source);
    }

    private Portfolio lockTarget(Long userId, Long targetId) {
        try {
            return portfolioService.getOwnedForUpdate(userId, targetId);
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException(TARGET_NOT_FOUND_MESSAGE);
        }
    }

    private static String pendingTransactionsMessage(long transactions) {
        return transactions == 1
                ? "El portafolio tiene 1 transacción: elegí moverla a otro portafolio o eliminarla"
                : "El portafolio tiene %d transacciones: elegí moverlas a otro portafolio o eliminarlas"
                        .formatted(transactions);
    }
}
