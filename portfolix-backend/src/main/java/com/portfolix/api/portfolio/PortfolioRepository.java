package com.portfolix.api.portfolio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Todas las consultas filtran por usuario: nunca se busca un portafolio solo por su id.
 */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findAllByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<Portfolio> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /**
     * Igual que findByIdAndUserId, pero bloquea la fila (SELECT ... FOR UPDATE) hasta que termine
     * la transacción: otra operación que pida el mismo bloqueo espera su turno.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.id = :id and p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    // lower() igual que el índice único uq_portfolios_user_name, para que ambos opinen lo mismo.
    @Query("""
            select count(p) > 0 from Portfolio p
            where p.user.id = :userId and lower(p.name) = lower(:name)
            """)
    boolean existsByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);

    @Query("""
            select count(p) > 0 from Portfolio p
            where p.user.id = :userId and lower(p.name) = lower(:name) and p.id <> :excludedId
            """)
    boolean existsByUserIdAndNameExcluding(@Param("userId") Long userId, @Param("name") String name,
                                           @Param("excludedId") Long excludedId);
}
