package com.portfolix.api.auth.token;

import com.portfolix.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** Token de un solo uso que viaja en el link de un mail. En la base se guarda solo su hash. */
@Entity
@Table(name = "email_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private EmailTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /**
     * Lo fija el {@code Clock} de la app (no {@code @CreationTimestamp}) porque se compara contra él
     * para no mandar más de un mail por minuto.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /** Solo en los cambios de mail: el mail que se aplica al confirmar el link. */
    @Column(name = "new_email", length = 254, updatable = false)
    private String newEmail;

    public EmailToken(User user, EmailTokenPurpose purpose, String tokenHash, Instant createdAt, Instant expiresAt) {
        this(user, purpose, tokenHash, createdAt, expiresAt, null);
    }

    public EmailToken(User user, EmailTokenPurpose purpose, String tokenHash, Instant createdAt, Instant expiresAt,
                      String newEmail) {
        this.user = user;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.newEmail = newEmail;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }
}
