package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Historial de precios: gráficos de tendencia y cierres diarios para el análisis de un período.
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

    /**
     * Cierres diarios de varios activos (para el análisis de un período), por símbolo. Se piden todos a la
     * vez, porque cada fuente tarda segundos, y se espera a que respondan. Un activo sin cierres en esas
     * fechas (todavía no cotizaba) no es un error: viene sin datos y quien llama decide qué precio usar.
     *
     * @throws com.portfolix.api.common.exception.ServiceUnavailableException si una fuente no respondió nunca
     */
    Map<String, DailyCloses> dailyCloses(Collection<ClosesRequest> requests);
}
