package com.portfolix.api.account;

import com.portfolix.api.account.dto.ChangePasswordRequest;
import com.portfolix.api.account.dto.DeleteAccountRequest;
import com.portfolix.api.account.dto.DeletionSummaryResponse;
import com.portfolix.api.account.dto.EmailChangeRequest;
import com.portfolix.api.auth.RefreshTokenCookies;
import com.portfolix.api.security.CurrentSessionId;
import com.portfolix.api.security.CurrentUserId;
import com.portfolix.api.security.ratelimit.RateLimitPolicy;
import com.portfolix.api.security.ratelimit.RateLimited;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Cuenta del usuario autenticado. {@code GET /me} y las preferencias están en el módulo user.
 * Los endpoints que reciben la contraseña usan el rate limit de login.
 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AccountService accountService;
    private final TransactionExportService transactionExportService;
    private final RefreshTokenCookies cookies;

    public AccountController(AccountService accountService, TransactionExportService transactionExportService,
                             RefreshTokenCookies cookies) {
        this.accountService = accountService;
        this.transactionExportService = transactionExportService;
        this.cookies = cookies;
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RateLimited(RateLimitPolicy.LOGIN)
    public void changePassword(@CurrentUserId Long userId, @CurrentSessionId String sessionId,
                               @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(userId, sessionId, request.currentPassword(), request.newPassword());
    }

    /** 202: el mail con el link sale después, en segundo plano. */
    @PostMapping("/email-change")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RateLimited(RateLimitPolicy.EMAIL)
    public void requestEmailChange(@CurrentUserId Long userId, @Valid @RequestBody EmailChangeRequest request) {
        accountService.requestEmailChange(userId, request.currentPassword(), request.newEmail());
    }

    @GetMapping("/deletion-summary")
    public DeletionSummaryResponse deletionSummary(@CurrentUserId Long userId) {
        return accountService.deletionSummary(userId);
    }

    /**
     * Todo el historial de transacciones en CSV, con el formato que corresponde al separador decimal del
     * usuario. {@code Content-Disposition: attachment} hace que el navegador lo descargue con ese nombre
     * (CORS ya expone ese header, así el front puede leerlo).
     */
    @GetMapping("/export/transactions.csv")
    public ResponseEntity<byte[]> exportTransactions(@CurrentUserId Long userId) {
        TransactionExportService.CsvFile file = transactionExportService.exportTransactions(userId);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }

    /** También borra la cookie del refresh token, como el logout. */
    @DeleteMapping
    @RateLimited(RateLimitPolicy.LOGIN)
    public ResponseEntity<Void> deleteAccount(@CurrentUserId Long userId,
                                              @Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(userId, request.currentPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }
}
