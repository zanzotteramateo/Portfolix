package com.portfolix.api.transaction.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Una página del historial más el resumen. {@code page.totalElements} es la cantidad de la lista
 * con todos los filtros ("20 operaciones"); {@code summary} no aplica el filtro de tipo.
 */
public record TransactionPageResponse(
        List<TransactionResponse> content,
        PageInfo page,
        TransactionSummary summary
) {

    /**
     * @param number página actual, empieza en 0
     */
    public record PageInfo(int number, int size, long totalElements, int totalPages) {
    }

    public static TransactionPageResponse of(Page<TransactionResponse> page, TransactionSummary summary) {
        PageInfo info = new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return new TransactionPageResponse(page.getContent(), info, summary);
    }
}
