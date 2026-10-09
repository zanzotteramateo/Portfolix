package com.portfolix.api.portfolio;

import com.portfolix.api.portfolio.dto.DuplicatePortfolioRequest;
import com.portfolix.api.portfolio.dto.PortfolioRequest;
import com.portfolix.api.portfolio.dto.PortfolioResponse;
import com.portfolix.api.security.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioDeletionService portfolioDeletionService;
    private final PortfolioDuplicationService portfolioDuplicationService;

    public PortfolioController(PortfolioService portfolioService, PortfolioDeletionService portfolioDeletionService,
                               PortfolioDuplicationService portfolioDuplicationService) {
        this.portfolioService = portfolioService;
        this.portfolioDeletionService = portfolioDeletionService;
        this.portfolioDuplicationService = portfolioDuplicationService;
    }

    @GetMapping
    public List<PortfolioResponse> list(@CurrentUserId Long userId) {
        return portfolioService.list(userId);
    }

    @PostMapping
    public ResponseEntity<PortfolioResponse> create(@CurrentUserId Long userId,
                                                    @Valid @RequestBody PortfolioRequest request) {
        PortfolioResponse created = portfolioService.create(userId, request.name());
        // Header Location con la URL del recurso nuevo: /api/v1/portfolios/{id}
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public PortfolioResponse get(@CurrentUserId Long userId, @PathVariable Long id) {
        return portfolioService.get(userId, id);
    }

    @PatchMapping("/{id}")
    public PortfolioResponse rename(@CurrentUserId Long userId, @PathVariable Long id,
                                    @Valid @RequestBody PortfolioRequest request) {
        return portfolioService.rename(userId, id, request.name());
    }

    /** "Duplicar como nuevo": copia el portafolio con todas sus transacciones. El body (el nombre) es opcional. */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<PortfolioResponse> duplicate(@CurrentUserId Long userId, @PathVariable Long id,
                                                       @Valid @RequestBody(required = false)
                                                       DuplicatePortfolioRequest request) {
        String name = request == null || request.name() == null ? null : request.name().trim();
        PortfolioResponse copy = portfolioDuplicationService.duplicate(userId, id, name);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/portfolios/{id}").buildAndExpand(copy.id()).toUri();
        return ResponseEntity.created(location).body(copy);
    }

    /**
     * {@code DELETE /portfolios/7?moveTransactionsTo=3}: mueve las transacciones al 3 y elimina el 7.
     * {@code DELETE /portfolios/7?deleteTransactions=true}: elimina el 7 con sus transacciones.
     * Sin parámetros solo se puede eliminar un portafolio vacío.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long id,
                                       @RequestParam(required = false) Long moveTransactionsTo,
                                       @RequestParam(defaultValue = "false") boolean deleteTransactions) {
        portfolioDeletionService.delete(userId, id, moveTransactionsTo, deleteTransactions);
        return ResponseEntity.noContent().build();
    }
}
