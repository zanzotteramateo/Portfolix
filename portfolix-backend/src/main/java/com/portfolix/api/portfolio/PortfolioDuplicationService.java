package com.portfolix.api.portfolio;

import com.portfolix.api.portfolio.dto.PortfolioResponse;
import com.portfolix.api.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Duplicar como nuevo": crea un portafolio con todas las transacciones del original (mismas fechas,
 * cantidades, precios y notas), para simular escenarios sin tocar el original.
 * <p>
 * Está separado de PortfolioService por lo mismo que PortfolioDeletionService: necesita a
 * TransactionService, que ya usa a PortfolioService (si no, la dependencia sería circular).
 */
@Service
public class PortfolioDuplicationService {

    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final TransactionService transactionService;

    public PortfolioDuplicationService(PortfolioService portfolioService, PortfolioRepository portfolioRepository,
                                       TransactionService transactionService) {
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.transactionService = transactionService;
    }

    /**
     * Duplica el portafolio. Sin nombre, la copia se llama "Nombre (copia)", o "Nombre (copia 2)" si ese
     * ya existe, y así. Respeta el máximo de portafolios y el nombre único, igual que crear uno.
     */
    @Transactional
    public PortfolioResponse duplicate(Long userId, Long portfolioId, String name) {
        // Bloquea el original mientras se copia: nadie le agrega, mueve ni borra transacciones a mitad de la copia.
        Portfolio source = portfolioService.getOwnedForUpdate(userId, portfolioId);
        String copyName = name != null ? name : availableCopyName(userId, source.getName());
        Portfolio copy = portfolioService.createPortfolio(userId, copyName);
        transactionService.copyAll(source, copy);
        return PortfolioResponse.from(copy);
    }

    /** Termina enseguida: con el máximo de 20 portafolios, a lo sumo 20 nombres pueden estar ocupados. */
    private String availableCopyName(Long userId, String original) {
        int number = 1;
        while (portfolioRepository.existsByUserIdAndName(userId, copyName(original, number))) {
            number++;
        }
        return copyName(original, number);
    }

    /** "Nombre (copia)", "Nombre (copia 2)"... Si no entra en el máximo, se recorta el nombre, no el sufijo. */
    static String copyName(String original, int number) {
        String suffix = number == 1 ? " (copia)" : " (copia %d)".formatted(number);
        int maxBaseLength = PortfolioService.MAX_NAME_LENGTH - suffix.length();
        String base = original.length() <= maxBaseLength ? original : original.substring(0, maxBaseLength).strip();
        return base + suffix;
    }
}
