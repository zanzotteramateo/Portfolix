package com.portfolix.api.market;

import java.util.Map;

/**
 * CoinGecko identifica cada moneda con un id propio, no con el símbolo (ej.: Bitcoin es "bitcoin",
 * no "BTC"). Mapeo a mano porque son pocas y cambian poco: si se agrega una cripto nueva al catálogo
 * (V7__seed_assets.sql), hay que agregarle su id acá también.
 */
final class CoinGeckoIds {

    private static final Map<String, String> BY_SYMBOL = Map.ofEntries(
            Map.entry("BTC", "bitcoin"),
            Map.entry("ETH", "ethereum"),
            Map.entry("USDT", "tether"),
            Map.entry("USDC", "usd-coin"),
            Map.entry("SOL", "solana"),
            Map.entry("ADA", "cardano"),
            Map.entry("BNB", "binancecoin"),
            Map.entry("XRP", "ripple"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("DOT", "polkadot"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("LINK", "chainlink"),
            Map.entry("LTC", "litecoin"),
            Map.entry("POL", "polygon-ecosystem-token")
    );

    private CoinGeckoIds() {
    }

    static String of(String symbol) {
        String id = BY_SYMBOL.get(symbol);
        if (id == null) {
            throw new IllegalStateException("No hay id de CoinGecko para " + symbol + " (agregalo a CoinGeckoIds)");
        }
        return id;
    }
}
