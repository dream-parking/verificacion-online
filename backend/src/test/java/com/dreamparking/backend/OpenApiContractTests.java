package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code docs/openapi.json} is what the front-end and mobile teams receive. This test fails when the API changed and
 * the file did not. Regenerate it with {@code ./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OpenApiContractTests {

	private static final Path PUBLISHED = Path.of("..", "docs", "openapi.json");

	@Autowired
	MockMvc mvc;

	@Test
	void publishedOpenApiFileMatchesTheApi() throws Exception {
		String generated = canonical(mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8));

		if (Boolean.getBoolean("openapi.update")) {
			Files.writeString(PUBLISHED, generated, StandardCharsets.UTF_8);
		}

		assertThat(PUBLISHED).as("docs/openapi.json (run with -Dopenapi.update=true to create it)").exists();
		assertThat(Files.readString(PUBLISHED, StandardCharsets.UTF_8))
			.as("docs/openapi.json is out of date: regenerate it with -Dopenapi.update=true")
			.isEqualTo(generated);
	}

	@Test
	void everyOperationHasAUniqueIdAndErrorsDescribeTheirBody() throws Exception {
		String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		var root = new ObjectMapper().readTree(json);

		var ids = new java.util.HashSet<String>();
		root.get("paths").forEach(path -> path.forEach(operation -> {
			assertThat(ids.add(operation.get("operationId").asText())).as("duplicate operationId").isTrue();
			assertThat(operation.get("responses").properties().stream().anyMatch(r -> r.getKey().startsWith("2")))
				.as("success response of " + operation.get("operationId").asText())
				.isTrue();
			operation.get("responses").properties().forEach(response -> {
				if (response.getKey().startsWith("2") && !response.getKey().equals("204")) {
					assertThat(response.getValue().has("content"))
						.as("response body of " + operation.get("operationId").asText() + " " + response.getKey())
						.isTrue();
				}
				if (!response.getKey().startsWith("2")) {
					assertThat(response.getValue().at("/content/application~1problem+json/schema/$ref").asText())
						.as("error body of " + operation.get("operationId").asText() + " " + response.getKey())
						.isEqualTo("#/components/schemas/ProblemDetail");
				}
			});
		}));
		assertThat(ids).contains("onboardingStart", "authLogin", "alertTake", "consoleRequestDetail");
		assertThat(root.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
	}

	private static String canonical(String json) throws Exception {
		ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
			.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
		return mapper.writeValueAsString(mapper.readValue(json, Object.class)) + "\n";
	}

}
