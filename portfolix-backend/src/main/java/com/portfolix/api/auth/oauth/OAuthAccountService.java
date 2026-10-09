package com.portfolix.api.auth.oauth;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cuentas de Portfolix que entran con un proveedor externo (por ahora, Google). */
@Service
public class OAuthAccountService {

    static final String OTHER_ACCOUNT_LINKED_MESSAGE =
            "Tu cuenta de Portfolix ya está vinculada a otra cuenta de Google";
    static final String RETRY_MESSAGE = "No pudimos completar el inicio de sesión con Google. Probá de nuevo";

    private final UserIdentityRepository identityRepository;
    private final UserService userService;

    public OAuthAccountService(UserIdentityRepository identityRepository, UserService userService) {
        this.identityRepository = identityRepository;
        this.userService = userService;
    }

    /**
     * Devuelve el usuario de Portfolix de esa cuenta externa:
     * <ol>
     *   <li>Si la cuenta ya está vinculada (proveedor + {@code sub}), ese usuario, aunque haya cambiado el mail.</li>
     *   <li>Si no, y hay una cuenta con ese mail, la vincula: el proveedor certificó que el mail es suyo.
     *       Si esa cuenta no estaba verificada, la verifica y le saca la contraseña: quien la creó nunca
     *       probó ser el dueño del mail, y si se la dejáramos podría seguir entrando a una cuenta ajena.</li>
     *   <li>Si no hay cuenta, la crea: verificada y sin contraseña.</li>
     * </ol>
     */
    @Transactional
    public User resolveUser(ExternalAccount account) {
        return identityRepository.findByProviderAndSubject(account.provider(), account.subject())
                .map(UserIdentity::getUser)
                .orElseGet(() -> link(account));
    }

    private User link(ExternalAccount account) {
        User user = userService.findByEmail(account.email())
                .map(existing -> claim(existing, account))
                .orElseGet(() -> userService.createExternalUser(account.name(), account.email()));
        try {
            identityRepository.saveAndFlush(new UserIdentity(user, account.provider(), account.subject()));
        } catch (DataIntegrityViolationException ex) {
            // Dos logins simultáneos con la misma cuenta nueva: el UNIQUE frena al segundo.
            throw new BusinessException(RETRY_MESSAGE);
        }
        return user;
    }

    private User claim(User user, ExternalAccount account) {
        if (identityRepository.existsByUserIdAndProvider(user.getId(), account.provider())) {
            throw new BusinessException(OTHER_ACCOUNT_LINKED_MESSAGE);
        }
        if (!user.isEmailVerified()) {
            userService.markEmailVerified(user.getId());
            userService.removePassword(user.getId());
        }
        return user;
    }
}
