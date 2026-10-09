package com.portfolix.api.account;

import com.portfolix.api.common.BusinessCalendar;
import com.portfolix.api.transaction.TransactionService;
import com.portfolix.api.user.preference.PreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * Exporta el historial de transacciones del usuario (botón "Descargar CSV" del modal de eliminar cuenta).
 * Junta dos módulos: las transacciones y la preferencia de separador decimal, que define el formato.
 */
@Service
public class TransactionExportService {

    private final TransactionService transactionService;
    private final PreferenceService preferenceService;
    private final BusinessCalendar calendar;

    public TransactionExportService(TransactionService transactionService, PreferenceService preferenceService,
                                    BusinessCalendar calendar) {
        this.transactionService = transactionService;
        this.preferenceService = preferenceService;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public CsvFile exportTransactions(Long userId) {
        String csv = transactionService.exportCsv(userId, preferenceService.decimalSeparator(userId));
        String filename = "portfolix-transacciones-%s.csv".formatted(calendar.today());
        return new CsvFile(filename, csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Un archivo listo para descargar. */
    public record CsvFile(String filename, byte[] content) {
    }
}
