package com.portfolix.api.holding;

import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.Rounding;
import com.portfolix.api.holding.dto.DashboardSummaryResponse;
import com.portfolix.api.holding.dto.DashboardSummaryResponse.FxInfo;
import com.portfolix.api.holding.dto.DashboardSummaryResponse.TypeAllocation;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.market.FxQuote;
import com.portfolix.api.market.FxRateProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    /** Los porcentajes de la distribución se reparten en décimas: 100 % = 1000 décimas. */
    private static final BigDecimal TENTHS_IN_100_PERCENT = BigDecimal.valueOf(1000);

    private final HoldingService holdingService;
    private final FxRateProvider fxRateProvider;

    public DashboardService(HoldingService holdingService, FxRateProvider fxRateProvider) {
        this.holdingService = holdingService;
        this.fxRateProvider = fxRateProvider;
    }

    /**
     * Totales del dashboard, a partir de las mismas posiciones que la tabla de activos.
     * Se suman los montos ya redondeados de cada activo: así el capital actual de la cabecera
     * es exactamente la suma de la columna "capital actual" de la tabla.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long userId, Long portfolioId, Currency currency) {
        BigDecimal currentValue = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal bought = BigDecimal.ZERO;
        int holdingsCount = 0;
        Map<AssetType, BigDecimal> valueByType = new EnumMap<>(AssetType.class);
        for (AssetType type : AssetType.values()) {
            valueByType.put(type, BigDecimal.ZERO);
        }

        // Incluye los activos vendidos por completo: aportan su ganancia realizada y lo que se pagó por ellos.
        List<AssetPosition> positions = holdingService.positions(userId, portfolioId, null, currency);
        for (AssetPosition position : positions) {
            BigDecimal value = Rounding.amount(position.currentValue());
            currentValue = currentValue.add(value);
            invested = invested.add(Rounding.amount(position.investedCapital()));
            realized = realized.add(Rounding.amount(position.realizedPnl()));
            bought = bought.add(position.totalBought());
            valueByType.merge(position.asset().getType(), value, BigDecimal::add);
            if (position.isOpen()) {
                holdingsCount++;
            }
        }

        BigDecimal unrealized = currentValue.subtract(invested);
        BigDecimal totalPnl = unrealized.add(realized);
        FxQuote fx = fxRateProvider.currentQuote();
        return new DashboardSummaryResponse(
                currency,
                currentValue,
                invested,
                totalPnl,
                // Sobre todo lo comprado alguna vez: compraste por 1.000, vendiste la mitad ganando 250
                // y lo que queda gana 100 → +35 %. Sobre lo invertido hoy (500) daría +70 %.
                Rounding.percent(totalPnl, Rounding.amount(bought)),
                unrealized,
                realized,
                holdingsCount,
                distribution(valueByType),
                new FxInfo(fx.type(), fx.rate()),
                HoldingService.oldestPriceUpdate(positions.stream().filter(AssetPosition::isOpen).toList()));
    }

    /**
     * Porcentaje de cada tipo con 1 decimal, sumando exactamente 100 (método del mayor resto).
     * Redondear cada uno por separado puede dar 99,9 o 100,1: por ejemplo tres tipos iguales
     * darían 33,3 + 33,3 + 33,3. Acá se trunca cada uno y las décimas que faltan van a los que
     * perdieron más al truncar: 33,4 + 33,3 + 33,3.
     */
    static List<TypeAllocation> distribution(Map<AssetType, BigDecimal> valueByType) {
        BigDecimal total = valueByType.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return valueByType.entrySet().stream()
                    .map(entry -> new TypeAllocation(entry.getKey(), entry.getValue(), null))
                    .toList();
        }

        record Share(AssetType type, BigDecimal value, BigDecimal exactTenths, long tenths) {
            BigDecimal remainder() {
                return exactTenths.subtract(BigDecimal.valueOf(tenths));
            }
        }
        List<Share> shares = new ArrayList<>();
        long assigned = 0;
        for (Map.Entry<AssetType, BigDecimal> entry : valueByType.entrySet()) {
            BigDecimal exact = entry.getValue().multiply(TENTHS_IN_100_PERCENT)
                    .divide(total, CurrencyConverter.PRECISION);
            long truncated = exact.setScale(0, RoundingMode.DOWN).longValueExact();
            shares.add(new Share(entry.getKey(), entry.getValue(), exact, truncated));
            assigned += truncated;
        }

        List<Share> byRemainderDesc = shares.stream()
                .sorted(Comparator.comparing(Share::remainder).reversed()
                        .thenComparing(share -> share.type().ordinal()))
                .toList();
        Map<AssetType, Long> finalTenths = new EnumMap<>(AssetType.class);
        long missing = TENTHS_IN_100_PERCENT.longValueExact() - assigned;
        for (Share share : byRemainderDesc) {
            finalTenths.put(share.type(), share.tenths() + (missing-- > 0 ? 1 : 0));
        }

        return shares.stream()
                .map(share -> new TypeAllocation(share.type(), share.value(),
                        BigDecimal.valueOf(finalTenths.get(share.type()), 1)))
                .toList();
    }
}
