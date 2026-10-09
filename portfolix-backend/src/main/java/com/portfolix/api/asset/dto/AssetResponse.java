package com.portfolix.api.asset.dto;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;

public record AssetResponse(String symbol, String name, AssetType type, Currency currency) {

    public static AssetResponse from(Asset asset) {
        return new AssetResponse(asset.getSymbol(), asset.getName(), asset.getType(), asset.getCurrency());
    }
}
