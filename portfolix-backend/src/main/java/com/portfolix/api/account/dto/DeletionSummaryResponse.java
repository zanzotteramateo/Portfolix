package com.portfolix.api.account.dto;

/**
 * Lo que se borra si el usuario elimina su cuenta, para el texto del modal: "Se van a eliminar de forma
 * definitiva: N portafolios y M activos · K transacciones registradas · tus preferencias y tu acceso con mail".
 *
 * @param assets activos distintos en los que operó alguna vez, en cualquiera de sus portafolios
 */
public record DeletionSummaryResponse(long portfolios, long assets, long transactions, String email) {
}
