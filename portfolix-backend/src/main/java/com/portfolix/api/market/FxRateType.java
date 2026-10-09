package com.portfolix.api.market;

/**
 * Tipos de dólar de Argentina. Se elige uno en {@code portfolix.market.fx-type}.
 * {@code apiName} es cómo lo llaman DolarApi y ArgentinaDatos en sus URLs.
 */
public enum FxRateType {
    OFICIAL("oficial"),
    MEP("bolsa"),
    CCL("contadoconliqui"),
    BLUE("blue");

    private final String apiName;

    FxRateType(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }
}
