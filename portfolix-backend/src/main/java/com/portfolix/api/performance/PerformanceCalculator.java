package com.portfolix.api.performance;

import com.portfolix.api.common.Rounding;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Cómo le fue a un activo (o a todo un portafolio) en un período. Clase pura, como {@code PositionCalculator} de holding.
 * <pre>
 * Al empezar tenías US$1.000; compraste 500 y vendiste 200; al final tenés 1.450.
 * Resultado = final − inicio − compras + ventas = 1.450 − 1.000 − 500 + 200 = +150
 * </pre>
 * No usa el precio promedio: solo cantidades, precios de mercado y la plata que entró y salió.
 * "Valor al inicio" es el valor al cierre del día anterior a {@code from}.
 */
final class PerformanceCalculator {

    /** Con un rango de más días, el gráfico lleva un punto por semana en vez de uno por día. */
    static final int MAX_DAILY_POINTS = 400;

    private PerformanceCalculator() {
    }

    /**
     * Una compra o venta del período, con el monto ya convertido a la moneda pedida.
     */
    record Flow(LocalDate date, TransactionType type, BigDecimal amount) {
    }

    /**
     * Un activo en el período, en la moneda pedida y con precisión completa.
     *
     * @param dailyValues valor de la tenencia al cierre de cada día del gráfico (ver {@link #chartDays});
     *                    los días sin tenencia no están
     */
    record AssetPeriod(BigDecimal startQuantity, BigDecimal endQuantity, BigDecimal startValue, BigDecimal endValue,
                       List<Flow> flows, Map<LocalDate, BigDecimal> dailyValues) {

        BigDecimal bought() {
            return total(flows, TransactionType.BUY);
        }

        BigDecimal sold() {
            return total(flows, TransactionType.SELL);
        }
    }

    /**
     * @param transactions      del activo, en orden cronológico; pueden ser de varios portafolios (las cantidades
     *                          se suman) y llegar hasta después de {@code to} (se ignoran)
     * @param unitValue         valor de una unidad al cierre de un día, en la moneda pedida. Solo se pide para
     *                          los días en que se tenía algo
     * @param toDisplayCurrency factor para pasar cada operación a la moneda pedida, con el dólar de su día
     */
    static AssetPeriod asset(List<Transaction> transactions, LocalDate from, LocalDate to,
                             Function<LocalDate, BigDecimal> unitValue,
                             Function<LocalDate, BigDecimal> toDisplayCurrency) {
        NavigableMap<LocalDate, BigDecimal> quantityAfter = new TreeMap<>(); // cantidad al cierre de cada día operado
        BigDecimal quantity = BigDecimal.ZERO;
        List<Flow> flows = new ArrayList<>();
        for (Transaction tx : transactions) {
            if (tx.getTradeDate().isAfter(to)) {
                break;
            }
            boolean buy = tx.getType() == TransactionType.BUY;
            quantity = buy ? quantity.add(tx.getQuantity()) : quantity.subtract(tx.getQuantity());
            quantityAfter.put(tx.getTradeDate(), quantity);
            // Las de antes del período solo cuentan para saber cuánto se tenía al empezar.
            if (!tx.getTradeDate().isBefore(from)) {
                BigDecimal amount = tx.getQuantity().multiply(tx.getPrice())
                        .multiply(toDisplayCurrency.apply(tx.getTradeDate()));
                flows.add(new Flow(tx.getTradeDate(), tx.getType(), amount));
            }
        }

        Map<LocalDate, BigDecimal> dailyValues = new LinkedHashMap<>();
        for (LocalDate day : chartDays(from, to)) {
            BigDecimal held = quantityOn(quantityAfter, day);
            if (held.signum() > 0) {
                dailyValues.put(day, held.multiply(unitValue.apply(day)));
            }
        }
        LocalDate dayBefore = from.minusDays(1);
        return new AssetPeriod(
                quantityOn(quantityAfter, dayBefore),
                quantityOn(quantityAfter, to),
                dailyValues.getOrDefault(dayBefore, BigDecimal.ZERO),
                dailyValues.getOrDefault(to, BigDecimal.ZERO),
                List.copyOf(flows),
                Collections.unmodifiableMap(dailyValues));
    }

    /**
     * Rendimiento ponderado por tiempo (Modified Dietz): resultado ÷ capital promedio del período.
     * El capital promedio es el valor inicial más cada compra (y menos cada venta) multiplicada por la
     * fracción del período en que estuvo invertida. El día de una operación cuenta como día invertido:
     * una compra cuenta desde el principio de su día y una venta hasta el final del suyo. Así "compré el
     * primer día y vendí el último" da exactamente la ganancia simple.
     * <pre>
     * Tenías 1.000 todo el año y ganaste 500; el último día pusiste 10.000 más, que quedaron igual.
     * Capital promedio = 1.000 + 10.000 × 1/365 ≈ 1.027 → rendimiento ≈ 48,67 %
     * (sobre todo lo puesto en juego daría 500 / 11.000 = 4,5 %, como si los 10.000 hubieran estado todo el año)
     * </pre>
     * Si al empezar no se tenía nada, el período arranca con la primera operación, y si al final ya no se
     * tiene nada, termina con la última: si no, una plata que estuvo un día de un año pesaría 1/365 y el
     * porcentaje se dispararía. El signo es siempre el del resultado.
     *
     * @param pnl el resultado en pesos o dólares, ya redondeado como se muestra
     * @return {@code null} si no hubo capital invertido
     */
    static BigDecimal percent(LocalDate from, LocalDate to, BigDecimal startValue, BigDecimal endValue,
                              List<Flow> flows, BigDecimal pnl) {
        if (startValue.signum() == 0 && flows.isEmpty()) {
            return null;
        }
        LocalDate start = startValue.signum() == 0 ? firstDate(flows) : from;
        LocalDate end = endValue.signum() == 0 && !flows.isEmpty() ? lastDate(flows) : to;
        BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) + 1);

        BigDecimal capital = startValue;
        for (Flow flow : flows) {
            boolean buy = flow.type() == TransactionType.BUY;
            long investedDays = ChronoUnit.DAYS.between(flow.date(), end) + (buy ? 1 : 0);
            BigDecimal weighted = flow.amount().multiply(BigDecimal.valueOf(investedDays))
                    .divide(days, CurrencyConverter.PRECISION);
            capital = buy ? capital.add(weighted) : capital.subtract(weighted);
        }
        return capital.signum() > 0 ? Rounding.percent(pnl, capital) : null;
    }

    /**
     * Días del gráfico: el anterior a {@code from} (el valor inicial) y uno por día hasta {@code to}. Si son más
     * de {@link #MAX_DAILY_POINTS}, uno por semana contando hacia atrás desde {@code to}, que siempre está.
     */
    static List<LocalDate> chartDays(LocalDate from, LocalDate to) {
        LocalDate first = from.minusDays(1);
        int step = ChronoUnit.DAYS.between(first, to) > MAX_DAILY_POINTS ? 7 : 1;
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = to; day.isAfter(first); day = day.minusDays(step)) {
            days.add(day);
        }
        days.add(first);
        Collections.reverse(days);
        return days;
    }

    static BigDecimal total(List<Flow> flows, TransactionType type) {
        return flows.stream()
                .filter(flow -> flow.type() == type)
                .map(Flow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal quantityOn(NavigableMap<LocalDate, BigDecimal> quantityAfter, LocalDate day) {
        Map.Entry<LocalDate, BigDecimal> entry = quantityAfter.floorEntry(day);
        return entry != null ? entry.getValue() : BigDecimal.ZERO;
    }

    private static LocalDate firstDate(List<Flow> flows) {
        return flows.stream().map(Flow::date).min(LocalDate::compareTo).orElseThrow();
    }

    private static LocalDate lastDate(List<Flow> flows) {
        return flows.stream().map(Flow::date).max(LocalDate::compareTo).orElseThrow();
    }
}
