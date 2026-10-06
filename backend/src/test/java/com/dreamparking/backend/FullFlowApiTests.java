package com.dreamparking.backend;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Mobile app flow from start to submit, then the console: request views, account opening and alert workflow. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class FullFlowApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void customerCompletesOnboardingAndConsoleWorksTheAlert() throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()), "$.id");

		// ---- mobile app steps
		perform(post("/api/onboarding/requests/{id}/sessions", requestId).header("User-Agent", "CeibaApp/1.0").content("""
				{"deviceFingerprint": "aa11·bb22·cc33", "deviceModel": "Galaxy A54", "operatingSystem": "Android",
				 "appVersion": "1.0.0", "countryIso": "SV", "typingSpeedCpm": 190, "typingPace": "NORMAL"}
				"""))
			.andExpect(status().isCreated());
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				"""))
			.andExpect(jsonPath("$.completedSteps").value(1));
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				"""))
			.andExpect(jsonPath("$.completedSteps").value(2));
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "MENOS_500"}
				"""))
			.andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountUsd": 320}
				"""))
			.andExpect(jsonPath("$.level").value("LOW"));
		perform(post("/api/onboarding/requests/{id}/submit", requestId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.completedSteps").value(5))
			.andExpect(jsonPath("$.riskLevel").value("LOW"))
			.andExpect(jsonPath("$.number").value(matchesPattern("SOL-\\d{4}-\\d{5}")));

		// ---- console: request views
		perform(get("/api/console/requests"))
			.andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(requestId)).value(hasItem("COMPLETED")));
		perform(get("/api/console/requests/{id}/steps", requestId)).andExpect(jsonPath("$.length()").value(5));
		perform(get("/api/console/requests/{id}/timeline", requestId))
			.andExpect(jsonPath("$[*].type").value(hasItem("REQUEST_SUBMITTED")))
			.andExpect(jsonPath("$[*].type").value(hasItem("SCORE_ASSIGNED")));
		perform(get("/api/console/requests/{id}/signals", requestId))
			.andExpect(jsonPath("$.ip").value("127.0.0.1"))
			.andExpect(jsonPath("$.device").value("Galaxy A54 · Android"));

		// ---- account opened in the core for the submitted request
		String accountId = read(perform(post("/api/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_test_1234", "lastFour": "1234"}
				""".formatted(requestId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.maskedNumber").value("•••• 1234"))
			.andExpect(jsonPath("$.openedAt").isNotEmpty()), "$.id");
		perform(post("/api/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_test_9999", "lastFour": "9999"}
				""".formatted(requestId)))
			.andExpect(status().isConflict());

		// ---- alert workflow
		String ana = userId("abeltran@ceiba.example");
		String luis = userId("lbarahona@ceiba.example");
		String alertId = read(perform(post("/api/console/alerts").content("""
				{"accountId": "%s", "typeCode": "PERFIL_EXCEDIDO", "reason": "Movimientos 4 veces por encima del perfil",
				 "evidence": {"factor": 4}}
				""".formatted(accountId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.criticality").value("CRITICAL"))
			.andExpect(jsonPath("$.status").value("UNASSIGNED")), "$.id");

		perform(post("/api/console/alerts/{id}/take", alertId).content(user(ana)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ASSIGNED"))
			.andExpect(jsonPath("$.assigneeName").value("Ana Beltrán"));
		perform(post("/api/console/alerts/{id}/take", alertId).content(user(luis))).andExpect(status().isConflict());
		perform(post("/api/console/alerts/{id}/review", alertId).content(user(luis))).andExpect(status().isForbidden());
		perform(post("/api/console/alerts/{id}/review", alertId).content(user(ana)))
			.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		perform(post("/api/console/alerts/{id}/close", alertId).content("""
				{"userId": "%s", "resolution": "FALSO_POSITIVO", "comment": "Pago de aguinaldo"}
				""".formatted(ana)))
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.resolution").value("FALSO_POSITIVO"));

		perform(get("/api/console/alerts/{id}/history", alertId)).andExpect(jsonPath("$.length()").value(4));
		perform(get("/api/console/alerts")).andExpect(jsonPath("$[*].id").value(not(hasItem(alertId))));
		perform(get("/api/console/users/{id}/access-audit", ana))
			.andExpect(jsonPath("$[0].action").value("TOMAR_ALERTA"));
	}

	@Test
	void requestCannotBeSubmittedBeforeAllSteps() throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")), "$.id");
		perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isConflict());
	}

	@Test
	void privacyNoticeMustBeAccepted() throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")), "$.id");
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": false}
				"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void basicDataIsValidated() throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")), "$.id");
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "123", "firstNames": "Ana", "lastNames": "Pérez", "mobilePhone": "2222-0000"}
				"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void consoleUsersCanBeCreatedButEmailsAreUnique() throws Exception {
		String body = """
				{"email": "nuevo@ceiba.example", "fullName": "Nuevo Analista", "jobTitle": "Analista",
				 "initials": "na", "role": "FRAUD_ANALYST"}
				""";
		perform(post("/api/console/users").content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.initials").value("NA"));
		perform(post("/api/console/users").content(body)).andExpect(status().isConflict());
	}

	private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

	private String userId(String email) {
		return jdbc.queryForObject("select id::text from usuario_consola where email = ?", String.class, email);
	}

	private static String user(String userId) {
		return "{\"userId\": \"" + userId + "\"}";
	}

}
