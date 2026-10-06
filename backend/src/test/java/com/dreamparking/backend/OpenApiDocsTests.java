package com.dreamparking.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The dev profile publishes the OpenAPI document and Swagger UI. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OpenApiDocsTests {

	@Autowired
	MockMvc mvc;

	@Test
	void apiDocsDescribeTheOnboardingEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Verificación Online API"))
			.andExpect(jsonPath("$.paths['/api/catalogs']").exists())
			.andExpect(jsonPath("$.paths['/api/onboarding/requests']").exists())
			.andExpect(jsonPath("$.paths['/api/onboarding/requests/{requestId}/income']").exists())
			.andExpect(jsonPath("$.paths['/api/onboarding/requests/{requestId}/expected-activity']").exists())
			.andExpect(jsonPath("$.paths['/api/onboarding/requests/{requestId}/risk-assessment']").exists())
			.andExpect(jsonPath("$.paths['/api/onboarding/requests/{requestId}/signals']").exists());
	}

	@Test
	void apiDocsDescribeTheConsoleEndpoints() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/console/requests']").exists())
			.andExpect(jsonPath("$.paths['/api/console/requests/{requestId}']").exists())
			.andExpect(jsonPath("$.paths['/api/console/score-rules']").exists())
			.andExpect(jsonPath("$.paths['/api/console/alerts']").exists())
			.andExpect(jsonPath("$.paths['/api/console/alerts/{alertId}/take']").exists())
			.andExpect(jsonPath("$.paths['/api/console/users']").exists());
	}

	@Test
	void swaggerUiIsServed() throws Exception {
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

}
