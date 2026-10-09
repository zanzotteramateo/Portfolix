package com.portfolix.api.transaction;

import com.portfolix.api.common.Currency;
import com.portfolix.api.security.CurrentUserId;
import com.portfolix.api.transaction.dto.TransactionPageResponse;
import com.portfolix.api.transaction.dto.TransactionRequest;
import com.portfolix.api.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Historial de transacciones. Todos los filtros son opcionales y se combinan.
     * Ej.: {@code /api/v1/transactions?assetSymbol=BTC&type=SELL&from=2026-08-01&page=0&size=50}
     * {@code currency} es la moneda del total convertido del resumen (ARS por defecto).
     */
    @GetMapping
    public TransactionPageResponse list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) String assetSymbol,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "ARS") Currency currency,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa") int page,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
            @Max(value = 100, message = "El tamaño de página puede ser hasta 100") int size) {
        TransactionFilter filter = new TransactionFilter(portfolioId, assetSymbol, type, from, to);
        return transactionService.list(userId, filter, currency, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@CurrentUserId Long userId, @Valid @RequestBody TransactionRequest request) {
        return transactionService.create(userId, request);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@CurrentUserId Long userId, @PathVariable Long id) {
        return transactionService.get(userId, id);
    }

    /**
     * Reemplaza todos los datos de la transacción: el body es el mismo formulario del alta.
     * Se rechaza (400) si con el cambio alguna venta se queda sin tenencia suficiente.
     */
    @PutMapping("/{id}")
    public TransactionResponse update(@CurrentUserId Long userId, @PathVariable Long id,
                                      @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(userId, id, request);
    }

    /** Se rechaza (400) si al borrarla alguna venta posterior se queda sin tenencia suficiente. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        transactionService.delete(userId, id);
    }
}
