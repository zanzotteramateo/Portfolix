package com.portfolix.api.portfolio;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.portfolio.dto.PortfolioResponse;
import com.portfolix.api.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PortfolioService {

    static final int MAX_PORTFOLIOS_PER_USER = 20;
    static final int MAX_NAME_LENGTH = 50;
    static final String NOT_FOUND_MESSAGE = "Portafolio no encontrado";
    static final String DUPLICATE_NAME_MESSAGE = "Ya tenés un portafolio con ese nombre";
    static final String LIMIT_REACHED_MESSAGE =
            "Alcanzaste el máximo de %d portafolios".formatted(MAX_PORTFOLIOS_PER_USER);

    private final PortfolioRepository portfolioRepository;
    private final UserService userService;

    public PortfolioService(PortfolioRepository portfolioRepository, UserService userService) {
        this.portfolioRepository = portfolioRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> list(Long userId) {
        return portfolioRepository.findAllByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(PortfolioResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countOwned(Long userId) {
        return portfolioRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse get(Long userId, Long portfolioId) {
        return PortfolioResponse.from(getOwned(userId, portfolioId));
    }

    @Transactional
    public PortfolioResponse create(Long userId, String rawName) {
        return PortfolioResponse.from(createPortfolio(userId, rawName));
    }

    /** Crea el portafolio respetando el máximo por usuario y el nombre único. Lo usa también la duplicación. */
    @Transactional
    Portfolio createPortfolio(Long userId, String rawName) {
        String name = rawName.trim();
        if (portfolioRepository.countByUserId(userId) >= MAX_PORTFOLIOS_PER_USER) {
            throw new BusinessException(LIMIT_REACHED_MESSAGE);
        }
        if (portfolioRepository.existsByUserIdAndName(userId, name)) {
            throw new BusinessException(DUPLICATE_NAME_MESSAGE);
        }
        Portfolio portfolio = new Portfolio(userService.getReference(userId), name);
        return saveCheckingUniqueName(portfolio);
    }

    @Transactional
    public PortfolioResponse rename(Long userId, Long portfolioId, String rawName) {
        String name = rawName.trim();
        Portfolio portfolio = getOwned(userId, portfolioId);
        // Excluye al propio portafolio: renombrar "jubilación" a "Jubilación" es válido.
        if (portfolioRepository.existsByUserIdAndNameExcluding(userId, name, portfolioId)) {
            throw new BusinessException(DUPLICATE_NAME_MESSAGE);
        }
        portfolio.setName(name);
        return PortfolioResponse.from(saveCheckingUniqueName(portfolio));
    }

    /**
     * Devuelve el portafolio solo si pertenece al usuario. Si no existe o es de otro usuario,
     * responde igual (404): así no se revela que ese id existe.
     * Todas las operaciones sobre un portafolio, de este módulo o de otros, pasan por acá.
     */
    @Transactional(readOnly = true)
    public Portfolio getOwned(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /**
     * Como {@link #getOwned}, pero bloquea el portafolio hasta que termine la transacción que llama.
     * Se usa al registrar ventas: dos ventas simultáneas del mismo portafolio se ejecutan de a una,
     * así la segunda ve la tenencia ya descontada por la primera.
     * MANDATORY: sin una transacción abierta el bloqueo se liberaría al instante y no serviría.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Portfolio getOwnedForUpdate(Long userId, Long portfolioId) {
        return portfolioRepository.findByIdAndUserIdForUpdate(portfolioId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /** Dos requests simultáneos con el mismo nombre: el índice único de la base frena al segundo. */
    private Portfolio saveCheckingUniqueName(Portfolio portfolio) {
        try {
            return portfolioRepository.saveAndFlush(portfolio);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(DUPLICATE_NAME_MESSAGE);
        }
    }
}
