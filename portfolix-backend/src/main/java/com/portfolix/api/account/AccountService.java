package com.portfolix.api.account;

import com.portfolix.api.account.dto.DeletionSummaryResponse;
import com.portfolix.api.auth.CredentialsChecker;
import com.portfolix.api.auth.EmailChangeService;
import com.portfolix.api.auth.token.RefreshTokenService;
import com.portfolix.api.portfolio.PortfolioService;
import com.portfolix.api.transaction.TransactionService;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Operaciones sensibles de la cuenta del usuario autenticado. Todas confirman antes la contraseña actual
 * ({@link CredentialsChecker#confirm}). Este módulo existe para orquestar varios módulos (auth, user,
 * portfolio, transaction) sin crear dependencias circulares: auth ya usa a user, así que estas
 * operaciones no pueden vivir en user.
 */
@Service
public class AccountService {

    private final UserService userService;
    private final CredentialsChecker credentialsChecker;
    private final RefreshTokenService refreshTokenService;
    private final EmailChangeService emailChangeService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;

    public AccountService(UserService userService, CredentialsChecker credentialsChecker,
                          RefreshTokenService refreshTokenService, EmailChangeService emailChangeService,
                          PortfolioService portfolioService, TransactionService transactionService) {
        this.userService = userService;
        this.credentialsChecker = credentialsChecker;
        this.refreshTokenService = refreshTokenService;
        this.emailChangeService = emailChangeService;
        this.portfolioService = portfolioService;
        this.transactionService = transactionService;
    }

    /**
     * Cambia la contraseña y cierra las sesiones de los otros dispositivos. La del dispositivo que lo pide
     * sigue abierta: {@code sessionId} es el claim {@code sid} de su access token.
     */
    @Transactional
    public void changePassword(Long userId, String sessionId, String currentPassword, String newPassword) {
        User user = userService.getById(userId);
        credentialsChecker.confirm(user, currentPassword);
        userService.changePassword(userId, newPassword);
        refreshTokenService.revokeOtherSessions(userId, parseSessionId(sessionId));
    }

    /** Pide el cambio de mail: el link va al mail nuevo y el cambio se aplica cuando se confirma. */
    @Transactional
    public void requestEmailChange(Long userId, String currentPassword, String newEmail) {
        User user = userService.getById(userId);
        credentialsChecker.confirm(user, currentPassword);
        emailChangeService.request(user, newEmail);
    }

    @Transactional(readOnly = true)
    public DeletionSummaryResponse deletionSummary(Long userId) {
        User user = userService.getById(userId);
        return new DeletionSummaryResponse(
                portfolioService.countOwned(userId),
                transactionService.countAssetsByUser(userId),
                transactionService.countByUser(userId),
                user.getEmail());
    }

    /** Elimina la cuenta y todo lo suyo (ver {@link UserService#deleteUser}). No se puede deshacer. */
    @Transactional
    public void deleteAccount(Long userId, String currentPassword) {
        User user = userService.getById(userId);
        credentialsChecker.confirm(user, currentPassword);
        userService.deleteUser(userId);
    }

    /** {@code null} si el token no tiene sesión o no es un UUID: en ese caso se cierran todas. */
    private static UUID parseSessionId(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
