package com.portfolix.api.auth.token;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

import static com.portfolix.api.auth.token.SecureTokens.hash;

/**
 * Tokens de un solo uso para los links de los mails (verificar la cuenta, restablecer la contraseña).
 * Los errores van con {@code field = "token"}: así el front distingue un link inválido de un dato mal cargado.
 */
@Service
public class EmailTokenService {

    static final String INVALID_LINK_MESSAGE = "El link no es válido o ya lo reemplazó uno más nuevo. Pedí otro";
    static final String EXPIRED_LINK_MESSAGE = "El link venció. Pedí uno nuevo";
    static final String USED_LINK_MESSAGE = "Este link ya se usó. Si lo necesitás, pedí uno nuevo";
    private static final String TOKEN_FIELD = "token";

    private final EmailTokenRepository repository;
    private final EmailTokenProperties properties;
    private final Clock clock;

    public EmailTokenService(EmailTokenRepository repository, EmailTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Emite un token y devuelve su valor para el link del mail (es la única vez que existe en claro).
     * Si el usuario tenía uno sin usar del mismo tipo, se borra: solo sirve el link del último mail.
     */
    @Transactional
    public String issue(User user, EmailTokenPurpose purpose) {
        return issue(user, purpose, null);
    }

    /** Igual que {@link #issue(User, EmailTokenPurpose)}, para un cambio de mail: el token guarda el mail nuevo. */
    @Transactional
    public String issueEmailChange(User user, String newEmail) {
        return issue(user, EmailTokenPurpose.EMAIL_CHANGE, newEmail);
    }

    private String issue(User user, EmailTokenPurpose purpose, String newEmail) {
        repository.deletePending(user.getId(), purpose);
        String value = SecureTokens.generate();
        Instant now = clock.instant();
        repository.save(new EmailToken(user, purpose, hash(value), now, now.plus(properties.ttl(purpose)), newEmail));
        return value;
    }

    /** Si a ese usuario se le mandó un mail de este tipo hace menos de {@code resend-cooldown}. */
    @Transactional(readOnly = true)
    public boolean issuedRecently(Long userId, EmailTokenPurpose purpose) {
        Instant since = clock.instant().minus(properties.resendCooldown());
        return repository.existsByUserIdAndPurposeAndCreatedAtAfter(userId, purpose, since);
    }

    /**
     * Busca el token de un link, usado o no: cada flujo decide qué hacer con uno usado.
     * Si no existe (o lo reemplazó uno más nuevo), responde 400.
     */
    @Transactional(readOnly = true)
    public EmailToken find(String value, EmailTokenPurpose purpose) {
        return repository.findByTokenHashAndPurpose(hash(value), purpose)
                .orElseThrow(() -> new BusinessException(TOKEN_FIELD, INVALID_LINK_MESSAGE));
    }

    /** Marca el token como usado. Responde 400 si ya se usó o si venció. */
    @Transactional
    public void redeem(EmailToken token) {
        Instant now = clock.instant();
        if (token.isUsed()) {
            throw new BusinessException(TOKEN_FIELD, USED_LINK_MESSAGE);
        }
        if (token.isExpired(now)) {
            throw new BusinessException(TOKEN_FIELD, EXPIRED_LINK_MESSAGE);
        }
        token.markUsed(now);
    }
}
