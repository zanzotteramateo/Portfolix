package com.portfolix.api.transaction;

import com.portfolix.api.common.Rounding;
import com.portfolix.api.user.preference.DecimalSeparator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Arma el CSV del historial de transacciones con los números como los espera la planilla del usuario:
 * <ul>
 *   <li>separador decimal coma (1.234,56): columnas separadas por {@code ;} y números {@code 1234,56}.
 *       Es lo que Excel configurado en español abre directo en columnas;</li>
 *   <li>separador decimal punto (1,234.56): columnas separadas por {@code ,} y números {@code 1234.56}.</li>
 * </ul>
 * Los números van sin separador de miles, que las planillas leen mal. El archivo empieza con el BOM
 * de UTF-8 (sin él, Excel muestra mal los acentos) y las líneas terminan en CRLF, como pide el estándar.
 */
final class TransactionCsv {

    static final String BOM = "﻿";
    private static final List<String> HEADER = List.of(
            "Fecha", "Portafolio", "Tipo", "Símbolo", "Activo", "Cantidad", "Precio", "Moneda", "Total", "Notas");
    private static final String LINE_END = "\r\n";
    /** Una celda que empieza así, Excel la interpreta como fórmula. */
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private TransactionCsv() {
    }

    /** Las transacciones tienen que venir con su portafolio y su activo ya cargados. */
    static String write(List<Transaction> transactions, DecimalSeparator separator) {
        char delimiter = separator == DecimalSeparator.COMMA ? ';' : ',';
        StringBuilder csv = new StringBuilder(BOM);
        appendRow(csv, HEADER, delimiter);
        for (Transaction transaction : transactions) {
            BigDecimal total = Rounding.amount(transaction.getQuantity().multiply(transaction.getPrice()));
            appendRow(csv, List.of(
                    transaction.getTradeDate().toString(),
                    text(transaction.getPortfolio().getName()),
                    transaction.getType() == TransactionType.BUY ? "Compra" : "Venta",
                    transaction.getAsset().getSymbol(),
                    transaction.getAsset().getName(),
                    number(transaction.getQuantity(), separator),
                    number(transaction.getPrice(), separator),
                    transaction.getCurrency().name(),
                    number(total, separator),
                    transaction.getNotes() == null ? "" : text(transaction.getNotes())
            ), delimiter);
        }
        return csv.toString();
    }

    private static void appendRow(StringBuilder csv, List<String> cells, char delimiter) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(delimiter);
            }
            csv.append(escape(cells.get(i), delimiter));
        }
        csv.append(LINE_END);
    }

    /** Entre comillas si tiene el separador, comillas o saltos de línea; las comillas internas se duplican. */
    private static String escape(String cell, char delimiter) {
        if (cell.indexOf(delimiter) < 0 && cell.indexOf('"') < 0 && cell.indexOf('\n') < 0 && cell.indexOf('\r') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }

    /**
     * Texto que escribió el usuario (nombre del portafolio, notas). Si empieza como una fórmula
     * ({@code =}, {@code +}, {@code -}, {@code @}), se le antepone un apóstrofo: si no, al abrir el archivo
     * Excel la ejecutaría ("CSV injection").
     */
    private static String text(String value) {
        return !value.isEmpty() && FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static String number(BigDecimal value, DecimalSeparator separator) {
        String plain = value.stripTrailingZeros().toPlainString();
        return separator == DecimalSeparator.COMMA ? plain.replace('.', ',') : plain;
    }
}
