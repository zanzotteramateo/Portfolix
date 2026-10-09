package com.portfolix.api.portfolio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La duplicación completa (con la base y las transacciones) se prueba en PortfolioDuplicationIntegrationTest. */
class PortfolioDuplicationServiceTest {

    @Test
    void copyName_addsTheSuffix_numberedFromTheSecondCopy() {
        assertThat(PortfolioDuplicationService.copyName("Jubilación", 1)).isEqualTo("Jubilación (copia)");
        assertThat(PortfolioDuplicationService.copyName("Jubilación", 2)).isEqualTo("Jubilación (copia 2)");
    }

    @Test
    void copyName_whenItDoesNotFit_cutsTheNameAndKeepsTheSuffix() {
        String longName = "Portafolio de largo plazo para jubilación futura"; // 48 caracteres: con " (copia 12)" no entra

        String copy = PortfolioDuplicationService.copyName(longName, 12);

        assertThat(copy).hasSize(50).endsWith(" (copia 12)").startsWith("Portafolio de largo plazo");
    }
}
