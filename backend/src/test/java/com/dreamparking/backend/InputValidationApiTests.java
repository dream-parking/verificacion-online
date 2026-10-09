package com.dreamparking.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * Texts with the wrong format are answered with 400 and the field that failed, whether they come in the body, the
 * query string or the path. The formats themselves are tested in {@code TextFormatsTests}.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InputValidationApiTests {

	private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

	@Autowired
	MockMvc mvc;

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Test
	void namesWithDigitsSymbolsOrInjectionsAreRejected() throws Exception {
		String id = startRequest();
		mvc.perform(put("/api/onboarding/requests/{id}/basic-data", id).contentType(JSON).content("""
				{"dui": "04567891-2", "firstNames": "Robert'); DROP TABLE customer;--", "lastNames": "Rivas 2",
				 "mobilePhone": "7123-4567"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.firstNames").value("Solo se permiten letras y espacios"))
			.andExpect(jsonPath("$.errors.lastNames").value("Solo se permiten letras y espacios"))
			.andExpect(jsonPath("$.errors.dui").doesNotExist());
	}

	@Test
	void freeTextRejectsEmojisAndMarkup() throws Exception {
		String id = startRequest();
		mvc.perform(put("/api/onboarding/requests/{id}/income", id).contentType(JSON).content("""
				{"sourceCode": "OTRO", "sourceDetail": "Ventas 🤑", "rangeCode": "menos_500"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.sourceDetail").exists())
			.andExpect(jsonPath("$.errors.rangeCode").exists())
			.andExpect(jsonPath("$.errors.sourceCode").doesNotExist());

		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		admin.perform(post("/api/console/users").contentType(JSON).content("""
				{"email": "nueva@ceiba.example", "fullName": "Nueva Persona", "jobTitle": "<b>Analista</b>\\u0000",
				 "role": "FRAUD_ANALYST", "initialPassword": "clave-inicial-123"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.jobTitle").exists());
	}

	@Test
	void queryParametersAndPathVariablesAreValidatedToo() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));

		admin.perform(get("/api/console/customers").param("dui", "' OR '1'='1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Some fields are invalid: dui"))
			.andExpect(jsonPath("$.errors.dui").value("Usa el formato 00000000-0"));
		admin.perform(get("/api/console/requests").param("q", "<script>"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.q").exists());
		admin.perform(get("/api/console/alerts").param("account", "1234; select"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.account").exists());
		admin.perform(get("/api/console/requests").param("q", "Marta Rivas")).andExpect(status().isOk());
	}

	@Test
	void bodyErrorsKeepTheirFieldNamesWhenTheMethodAlsoValidatesItsPath() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		String valid = """
				{"label": "Salario", "sortOrder": 1, "active": true}
				""";

		admin.perform(put("/api/console/admin/catalogs/income-sources/{code}", "salario 😀").contentType(JSON)
			.content(valid))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.code").exists());
		admin.perform(put("/api/console/admin/catalogs/income-sources/SALARIO").contentType(JSON).content("""
				{"label": "Salario 💵", "sortOrder": 1, "active": true}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Some fields are invalid: label"))
			.andExpect(jsonPath("$.errors.label").exists());
		admin.perform(put("/api/console/admin/catalogs/income-sources/SALARIO").contentType(JSON).content(valid))
			.andExpect(status().isOk());
	}

	@Test
	void newPasswordsCannotHaveEmojis() throws Exception {
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		admin.perform(post("/api/console/users").contentType(JSON).content("""
				{"email": "nueva@ceiba.example", "fullName": "Nueva Persona", "jobTitle": "Analista",
				 "role": "FRAUD_ANALYST", "initialPassword": "clave-larga-🔑"}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(
					"La contraseña no puede tener emojis: pueden escribirse distinto en otro teclado o dispositivo"));
	}

	private String startRequest() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
