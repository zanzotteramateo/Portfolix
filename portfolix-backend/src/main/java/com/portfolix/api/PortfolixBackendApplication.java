package com.portfolix.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class PortfolixBackendApplication {

	public static void main(String[] args) {
		// La app trabaja siempre en UTC, sin depender de la zona horaria de la máquina.
		// El front convierte a la zona del usuario al mostrar fechas.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(PortfolixBackendApplication.class, args);
	}

}
