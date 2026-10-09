package com.portfolix.api.transaction;

import com.portfolix.api.portfolio.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction>, TransactionSummaryRepository {

    /** Historial de un activo en un portafolio, en orden cronológico (y de alta dentro del mismo día). */
    List<Transaction> findAllByPortfolioIdAndAssetIdOrderByTradeDateAscIdAsc(Long portfolioId, Long assetId);

    /** Una transacción del usuario, con su portafolio y su activo. Vacío si no existe o es de otro usuario. */
    @EntityGraph(attributePaths = {"portfolio", "asset"})
    @Query("select t from Transaction t where t.id = :id and t.portfolio.user.id = :userId")
    Optional<Transaction> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Solo el id del portafolio de una transacción del usuario, sin cargar la transacción:
     * sirve para saber qué portafolio bloquear antes de leerla entera.
     */
    @Query("select t.portfolio.id from Transaction t where t.id = :id and t.portfolio.user.id = :userId")
    Optional<Long> findPortfolioIdByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Listado filtrado y paginado. {@code @EntityGraph} trae el portafolio y el activo de cada transacción
     * en la misma consulta: sin esto, armar la respuesta dispararía una consulta extra por cada
     * portafolio y cada activo distinto de la página (el problema "N+1").
     */
    @Override
    @EntityGraph(attributePaths = {"portfolio", "asset"})
    Page<Transaction> findAll(Specification<Transaction> condition, Pageable pageable);

    /** Igual que el anterior pero sin paginar (para calcular posiciones). */
    @Override
    @EntityGraph(attributePaths = {"portfolio", "asset"})
    List<Transaction> findAll(Specification<Transaction> condition, Sort sort);

    long countByPortfolioId(Long portfolioId);

    /** Las transacciones de un portafolio en orden cronológico, con el activo ya cargado (para copiarlas). */
    @EntityGraph(attributePaths = "asset")
    List<Transaction> findAllByPortfolioIdOrderByTradeDateAscIdAsc(Long portfolioId);

    @Query("select count(t) from Transaction t where t.portfolio.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /** En cuántos activos distintos operó el usuario, en cualquiera de sus portafolios. */
    @Query("select count(distinct t.asset.id) from Transaction t where t.portfolio.user.id = :userId")
    long countDistinctAssetsByUserId(@Param("userId") Long userId);

    /** Pasa todas las transacciones de un portafolio a otro con un solo UPDATE. */
    @Modifying
    @Query("update Transaction t set t.portfolio = :target where t.portfolio = :source")
    int moveAll(@Param("source") Portfolio source, @Param("target") Portfolio target);
}
