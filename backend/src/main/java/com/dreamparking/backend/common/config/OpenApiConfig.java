package com.dreamparking.backend.common.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document the front-end and mobile teams generate their clients from: stable operation ids, the error
 * body ({@code application/problem+json}) on every error response, the bearer scheme and the environments.
 */
@Configuration
public class OpenApiConfig {

	static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

	@Bean
	OpenAPI verificacionOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Verificación Online API")
				.version("v1")
				.description("""
						API del flujo de onboarding móvil (apertura de cuenta), del score de riesgo y de la consola administrativa.

						- Rutas `/api/onboarding/**` y `/api/catalogs`: públicas (app móvil).
						- Rutas `/api/console/**`: requieren `Authorization: Bearer <token>` de `POST /api/console/auth/login`.
						- Errores: `application/problem+json` con `status`, `title` y `detail`.
						- Los campos que aparecen como opcionales en los esquemas de respuesta pueden venir `null`."""))
			.servers(List.of(new Server().url("https://api.dev.identidad.alambritos.online").description("Dev"),
					new Server().url("https://api.qa.identidad.alambritos.online").description("QA"),
					new Server().url("http://localhost:8080").description("Local")))
			.components(new Components().addSecuritySchemes("bearerAuth",
					new SecurityScheme().type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Token de POST /api/console/auth/login. Solo la consola lo usa; el flujo móvil es público."))
				.addSchemas("ProblemDetail", problemDetailSchema()));
	}

	/** {@code operationId} = controller + method (e.g. {@code onboardingStart}); errors get the problem body. */
	@Bean
	OperationCustomizer operationCustomizer() {
		return (operation, handlerMethod) -> {
			String controller = handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
			String method = handlerMethod.getMethod().getName();
			operation.setOperationId(lowerFirst(controller) + method.substring(0, 1).toUpperCase(Locale.ROOT)
					+ method.substring(1));

			ApiResponses responses = operation.getResponses();
			if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
				responses.putIfAbsent("401", new ApiResponse().description("Falta el token o ya no es válido"));
			}
			responses.forEach((code, response) -> {
				if (!code.startsWith("2") && (response.getContent() == null || response.getContent().isEmpty())) {
					response.setContent(new Content().addMediaType(PROBLEM_MEDIA_TYPE,
							new MediaType().schema(new Schema<>().$ref("#/components/schemas/ProblemDetail"))));
				}
			});
			return operation;
		};
	}

	private static Schema<?> problemDetailSchema() {
		return new ObjectSchema().description("Error de la API (RFC 9457)")
			.properties(Map.of("type", new StringSchema().example("about:blank"), "title",
					new StringSchema().example("Not Found"), "status", new IntegerSchema().example(404), "detail",
					new StringSchema().example("Onboarding request not found"), "instance", new StringSchema()));
	}

	private static String lowerFirst(String text) {
		return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
	}

}
