package com.portfolix.api;

import org.springframework.boot.SpringApplication;

public class TestPortfolixBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(PortfolixBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
