package com.tp.facturacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FacturacionApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FacturacionApiApplication.class, args);
	}

}
