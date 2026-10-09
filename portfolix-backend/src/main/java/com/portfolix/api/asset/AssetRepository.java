package com.portfolix.api.asset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByActiveTrueOrderBySymbolAsc();

    Optional<Asset> findBySymbol(String symbol);

    List<Asset> findAllByType(AssetType type);
}
