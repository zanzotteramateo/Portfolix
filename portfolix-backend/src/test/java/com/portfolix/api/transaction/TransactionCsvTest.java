package com.portfolix.api.transaction;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.user.User;
import com.portfolix.api.user.preference.DecimalSeparator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.portfolix.api.transaction.TransactionType.BUY;
import static com.portfolix.api.transaction.TransactionType.SELL;
import static org.assertj.core.api.Assertions.assertThat;

class TransactionCsvTest {

    private static final String BOM = TransactionCsv.BOM;

    private final User user = new User("juan@email.com", "hash", "Juan Pérez", Instant.now());
    private final Portfolio retirement = new Portfolio(user, "Jubilación");
    private final Asset ypf = new Asset("YPFD", "YPF S.A.", AssetType.STOCK, Currency.ARS);
    private final Asset btc = new Asset("BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD);

    @Test
    void withDecimalComma_usesSemicolonsAndCommaDecimals() {
        String csv = TransactionCsv.write(List.of(
                transaction(retirement, ypf, BUY, "10", "25000.5", "2026-09-01", null),
                transaction(retirement, btc, SELL, "0.015", "60000", "2026-09-02", "Toma de ganancia")
        ), DecimalSeparator.COMMA);

        assertThat(csv).isEqualTo(BOM
                + "Fecha;Portafolio;Tipo;Símbolo;Activo;Cantidad;Precio;Moneda;Total;Notas\r\n"
                + "2026-09-01;Jubilación;Compra;YPFD;YPF S.A.;10;25000,5;ARS;250005;\r\n"
                + "2026-09-02;Jubilación;Venta;BTC;Bitcoin;0,015;60000;USD;900;Toma de ganancia\r\n");
    }

    @Test
    void withDecimalPeriod_usesCommasAndPeriodDecimals() {
        String csv = TransactionCsv.write(List.of(
                transaction(retirement, ypf, BUY, "10", "25000.5", "2026-09-01", null)
        ), DecimalSeparator.PERIOD);

        assertThat(csv).isEqualTo(BOM
                + "Fecha,Portafolio,Tipo,Símbolo,Activo,Cantidad,Precio,Moneda,Total,Notas\r\n"
                + "2026-09-01,Jubilación,Compra,YPFD,YPF S.A.,10,25000.5,ARS,250005,\r\n");
    }

    @Test
    void theTotal_isRoundedToCents() {
        String csv = TransactionCsv.write(List.of(
                transaction(retirement, btc, BUY, "0.1", "72400.123", "2026-09-01", null)
        ), DecimalSeparator.COMMA);

        assertThat(csv).contains(";0,1;72400,123;USD;7240,01;");
    }

    @Test
    void cellsWithTheDelimiterQuotesOrLineBreaks_goBetweenQuotes() {
        Portfolio longTerm = new Portfolio(user, "Ahorro, largo plazo");

        String csv = TransactionCsv.write(List.of(
                transaction(longTerm, ypf, BUY, "1", "100", "2026-09-01", "Dijo \"comprá\"\ny compré")
        ), DecimalSeparator.PERIOD);

        assertThat(csv).contains(",\"Ahorro, largo plazo\",")
                .endsWith(",\"Dijo \"\"comprá\"\"\ny compré\"\r\n");
    }

    @Test
    void textThatLooksLikeAFormula_getsAnApostrophe_soExcelDoesNotRunIt() {
        Portfolio aggressive = new Portfolio(user, "+Agresivo");

        String csv = TransactionCsv.write(List.of(
                transaction(aggressive, ypf, BUY, "1", "100", "2026-09-01", "=HYPERLINK(\"http://evil\")")
        ), DecimalSeparator.COMMA);

        assertThat(csv).contains(";'+Agresivo;").contains(";\"'=HYPERLINK(\"\"http://evil\"\")\"\r\n");
    }

    private static Transaction transaction(Portfolio portfolio, Asset asset, TransactionType type, String quantity,
                                           String price, String date, String notes) {
        return new Transaction(portfolio, asset, type, new BigDecimal(quantity), new BigDecimal(price),
                LocalDate.parse(date), notes);
    }
}
