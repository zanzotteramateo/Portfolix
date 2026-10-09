package com.portfolix.api.account;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El formato del CSV en detalle (comillas, fórmulas, redondeo) se prueba en TransactionCsvTest. */
class TransactionExportIntegrationTest extends ApiIntegrationTest {

    @Test
    void export_hasTheWholeHistory_inTheFormatOfTheUsersDecimalSeparator() throws Exception {
        RequestPostProcessor juan = authenticatedAs(createUser("Juan Pérez"));
        long retirement = createPortfolio(juan, "Jubilación");
        recordTransaction(juan, retirement, "YPFD", "BUY", "10", "25000.5", daysAgo(3));
        registerTransaction(juan, retirement, "YPFD", "SELL", "\"4\"", "\"26000\"", daysAgo(1), "\"Venta; parcial\"")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/me/export/transactions.csv").with(juan))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"portfolix-transacciones-" + LocalDate.now(ARGENTINA) + ".csv\""));

        // Separador coma (el de por defecto): columnas con ";" y decimales con coma, en orden cronológico.
        assertThat(export(juan)).isEqualTo("﻿"
                + "Fecha;Portafolio;Tipo;Símbolo;Activo;Cantidad;Precio;Moneda;Total;Notas\r\n"
                + daysAgo(3) + ";Jubilación;Compra;YPFD;YPF S.A.;10;25000,5;ARS;250005;\r\n"
                + daysAgo(1) + ";Jubilación;Venta;YPFD;YPF S.A.;4;26000;ARS;104000;\"Venta; parcial\"\r\n");

        mockMvc.perform(patch("/api/v1/me/preferences").with(juan)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decimalSeparator\": \"PERIOD\"}"))
                .andExpect(status().isOk());

        assertThat(export(juan)).contains(daysAgo(3) + ",Jubilación,Compra,YPFD,YPF S.A.,10,25000.5,ARS,250005,\r\n");
    }

    @Test
    void export_withoutTransactions_hasOnlyTheHeader() throws Exception {
        assertThat(export(authenticatedAs(createUser("Juan Pérez"))))
                .isEqualTo("﻿Fecha;Portafolio;Tipo;Símbolo;Activo;Cantidad;Precio;Moneda;Total;Notas\r\n");
    }

    private String export(RequestPostProcessor user) throws Exception {
        return mockMvc.perform(get("/api/v1/me/export/transactions.csv").with(user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
