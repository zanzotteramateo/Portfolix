package com.portfolix.api.asset;

import com.portfolix.api.asset.dto.AssetResponse;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class AssetService {

    static final String NOT_FOUND_MESSAGE = "Activo no encontrado";

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /**
     * Activos disponibles, con filtros opcionales por tipo y por texto (símbolo o nombre,
     * sin distinguir mayúsculas ni tildes: "energia" encuentra "Pampa Energía").
     * <p>
     * El catálogo tiene decenas de activos, así que se filtra en memoria: es más simple que
     * armar una consulta dinámica y en la fase 7 se puede cachear entero.
     */
    @Transactional(readOnly = true)
    public List<AssetResponse> listActive(AssetType type, String query) {
        String normalizedQuery = query == null || query.isBlank() ? null : normalize(query);
        return assetRepository.findAllByActiveTrueOrderBySymbolAsc().stream()
                .filter(asset -> type == null || asset.getType() == type)
                .filter(asset -> normalizedQuery == null
                        || normalize(asset.getSymbol()).contains(normalizedQuery)
                        || normalize(asset.getName()).contains(normalizedQuery))
                .map(AssetResponse::from)
                .toList();
    }

    /** Busca por símbolo sin distinguir mayúsculas ("btc" = "BTC"). Incluye activos inactivos. */
    @Transactional(readOnly = true)
    public Asset getBySymbol(String symbol) {
        return assetRepository.findBySymbol(symbol.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /** Símbolos de todos los activos de un tipo, incluidos los inactivos (alguien puede seguir teniéndolos). */
    @Transactional(readOnly = true)
    public List<String> symbolsOfType(AssetType type) {
        return assetRepository.findAllByType(type).stream().map(Asset::getSymbol).toList();
    }

    /** Minúsculas y sin tildes. */
    private static String normalize(String text) {
        return Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
