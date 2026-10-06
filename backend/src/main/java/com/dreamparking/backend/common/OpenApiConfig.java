package com.dreamparking.backend.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata shown at the top of Swagger UI. */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI verificacionOpenApi() {
		return new OpenAPI().info(new Info().title("Verificación Online API")
			.version("v1")
			.description("API del flujo de onboarding móvil (apertura de cuenta) y del score de riesgo."));
	}

}
