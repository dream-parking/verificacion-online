package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-82: the score adds up the signals captured so far and is calculated again on submit, so the telemetry that
 * arrives after the amount is declared counts, and the breakdown of every signal is kept with the assessment.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RiskScoreSignalsApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	@BeforeEach
	void signIn() {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void theScoreAddsTheTelemetryCapturedBeforeTheAmount() throws Exception {
		String id = startRequest();
		sendSignals(id, 310);

		send(id, "expected-activity", """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "500_1000"}
				""")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.modelVersion").value(4))
			.andExpect(jsonPath("$.signals[?(@.signal == 'AMOUNT')].points").value(7))
			.andExpect(jsonPath("$.signals[?(@.signal == 'TYPING_PACE')].points").value(10))
			.andExpect(jsonPath("$.signals[?(@.signal == 'TYPING_PACE')].observation")
				.value("Muy rápido, unos 310 caracteres por minuto"))
			.andExpect(jsonPath("$.signals[?(@.signal == 'DEVICE')].points").value(2))
			.andExpect(jsonPath("$.signals[?(@.signal == 'DEVICE')].observation")
				.value("Primera solicitud desde este teléfono"))
			.andExpect(jsonPath("$.signals[?(@.signal == 'IDENTITY_DOCUMENT')].points[0]").isEmpty());

		// the score is the sum of what the signals added, whatever the time of the day the test runs at
		String body = read(id, "risk-assessment").andReturn().getResponse().getContentAsString();
		List<Integer> points = JsonPath.read(body, "$.signals[?(@.points != null)].points");
		assertThat((Integer) JsonPath.read(body, "$.score")).isEqualTo(points.stream().mapToInt(Integer::intValue).sum());
		assertThat(points).hasSizeGreaterThanOrEqualTo(3);
	}

	@Test
	void submittingScoresAgainWithTheTelemetryThatArrivedLate() throws Exception {
		String id = startRequest();
		send(id, "privacy-consent", "{\"signalsAccepted\": true}").andExpect(status().isOk());
		send(id, "basic-data", """
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""").andExpect(status().isOk());
		DuiStep.complete(mvc, id, "01234567-8", "Ana Sofía", "Pérez López");
		send(id, "income", "{\"sourceCode\": \"SALARIO\", \"rangeCode\": \"HASTA_500\"}")
			.andExpect(status().isNoContent());
		send(id, "expected-activity", """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.signals[?(@.signal == 'TYPING_PACE')].points[0]").isEmpty());

		sendSignals(id, 95);
		em.flush();
		em.clear();
		mvc.perform(post("/api/onboarding/requests/{id}/submit", id).contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk());

		read(id, "risk-assessment").andExpect(jsonPath("$.signals[?(@.signal == 'TYPING_PACE')].points").value(5))
			.andExpect(jsonPath("$.signals[?(@.signal == 'AMOUNT')].points").value(4));
		// Two assessments, only the last one is current, and the timeline says what was assigned
		assertThat(jdbc.queryForObject("select count(*) from risk_assessment where request_id = ?::uuid", Integer.class,
				id)).isEqualTo(2);
		assertThat(jdbc.queryForObject(
				"select count(*) from risk_assessment where request_id = ?::uuid and is_current", Integer.class, id))
			.isEqualTo(1);
		assertThat(jdbc.queryForList("select description from request_event where request_id = ?::uuid and type = 'SCORE_ASSIGNED'",
				String.class, id)).hasSize(2).allMatch(d -> d.startsWith("Score de riesgo asignado: "));
		assertThat(jdbc.queryForObject("select count(*) from risk_assessment_signal s join risk_assessment a on a.id = s.assessment_id where a.request_id = ?::uuid and a.is_current",
				Integer.class, id)).isEqualTo(8);
	}

	@Test
	void theConsoleDetailCarriesTheBreakdown() throws Exception {
		String id = startRequest();
		send(id, "expected-activity", """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "MAS_1000"}
				""").andExpect(status().isOk());

		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.risk.score").value(18))
			.andExpect(jsonPath("$.risk.signals[*].signal").value(hasItem("AMOUNT")));
	}

	@Test
	void theConsoleListCarriesTheScore() throws Exception {
		String id = startRequest();
		send(id, "expected-activity", """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "MAS_1000"}
				""").andExpect(status().isOk());

		// newest first, so the request just made is on the first page
		mvc.perform(get("/api/console/requests"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == '" + id + "')].score").value(hasItem(18)));
	}

	private void sendSignals(String id, int typingSpeedCpm) throws Exception {
		send(id, "signals", """
				{"deviceFingerprint": "fp-%s", "deviceModel": "Galaxy A54", "operatingSystem": "Android",
				 "countryIso": "SV", "typingSpeedCpm": %d}
				""".formatted(UUID.randomUUID(), typingSpeedCpm)).andExpect(status().isNoContent());
		// The signals are read from a database view, so what was just written has to reach the database first
		// (in production each call is its own transaction).
		em.flush();
		em.clear();
	}

	private ResultActions send(String id, String resource, String body) throws Exception {
		return mvc.perform(put("/api/onboarding/requests/{id}/{resource}", id, resource)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions read(String id, String resource) throws Exception {
		return mvc.perform(get("/api/onboarding/requests/{id}/{resource}", id, resource));
	}

	private String startRequest() throws Exception {
		MockHttpServletRequestBuilder start = post("/api/onboarding/requests");
		return JsonPath.read(mvc.perform(start).andReturn().getResponse().getContentAsString(), "$.id");
	}

}
