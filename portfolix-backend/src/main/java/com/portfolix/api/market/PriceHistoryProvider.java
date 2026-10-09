package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;

import java.util.Optional;

/**
 * Historial de precios para los gráficos de tendencia.
 */
public interface PriceHistoryProvider {

    /**
     * Historial de un rango. Si todavía no está cargado, espera a la fuente (para el panel de detalle).
     *
     * @throws com.portfolix.api.common.exception.ServiceUnavailableException si no se pudo obtener nunca
     */
    PriceHistory history(Asset asset, HistoryRange range);

    /**
     * Historial solo si ya está cargado; si no, lo pide en segundo plano y devuelve vacío. Nunca espera
     * ni falla: las tendencias son decorativas y no pueden frenar ni romper la tabla de activos.
     */
    Optional<PriceHistory> cachedHistory(Asset asset, HistoryRange range);
}
