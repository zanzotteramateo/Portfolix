package com.portfolix.api.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHashAndPurpose(String tokenHash, EmailTokenPurpose purpose);

    boolean existsByUserIdAndPurposeAndCreatedAtAfter(Long userId, EmailTokenPurpose purpose, Instant since);

    /** Borra el token sin usar de ese tipo: el link del mail anterior deja de servir. */
    @Modifying
    @Query("""
            delete from EmailToken t
            where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null
            """)
    int deletePending(@Param("userId") Long userId, @Param("purpose") EmailTokenPurpose purpose);
}
