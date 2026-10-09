package com.portfolix.api.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Cierra todas las sesiones abiertas de un usuario. */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.user.id = :userId and t.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    /** Cierra todas las sesiones abiertas de un usuario menos una. */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.user.id = :userId and t.revokedAt is null and t.sessionId <> :sessionId
            """)
    int revokeAllByUserIdExceptSession(@Param("userId") Long userId, @Param("sessionId") UUID sessionId,
                                       @Param("now") Instant now);
}
