package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.service.RiskAssessmentService;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-58, VDI-82: edge cases of the score. Each amount range adds its points (an open-ended one the most); with
 * only the amount known the score is low; no data means not evaluated; invalid input leaves no score.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RiskScoreEdgeCaseTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OnboardingRequestRepository requests;

	@Autowired
	RiskAssessmentService riskAssessments;

	@Test
	void eachRangeAddsMorePointsThanTheOneBelow() throws Exception {
		assertScore("HASTA_200", 0, 200);
		assertScore("200_500", 3, 500);
		assertScore("500_1000", 6, 1000);
		assertScore("MAS_1000", 16, 1000.01);
	}

	@Test
	void theAssessmentExplainsEachSignalAndDoesNotUseARule() throws Exception {
		String id = startRequest();
		registerRange(id, "PAGO_SALARIO", "200_500");

		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id))
			.andExpect(jsonPath("$.ruleCode").isEmpty())
			.andExpect(jsonPath("$.ruleThreshold").isEmpty())
			.andExpect(jsonPath("$.evaluatedValue").value(500))
			.andExpect(jsonPath("$.signals.length()").value(9))
			.andExpect(jsonPath("$.signals[?(@.signal == 'RNPN')].points[0]").isEmpty());
	}

	@Test
	void withoutARangeTheRequestIsNotEvaluated() throws Exception {
		String id = startRequest();

		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id)).andExpect(status().isNotFound());

		OnboardingRequest request = requests.findById(UUID.fromString(id)).orElseThrow();
		RiskAssessmentResponse response = riskAssessments.evaluate(request, null);
		assertThat(response.level()).isEqualTo(RiskLevel.NOT_EVALUATED);
		assertThat(response.score()).isNull();
		assertThat(response.signals()).isEmpty();
		assertThat(response.evaluatedValue()).isNull();
	}

	@Test
	void invalidInputIsRejectedAndLeavesNoScore() throws Exception {
		String id = startRequest();
		String[] bodies = { "{\"transactionTypeCode\": \"PAGO_SALARIO\"}",
				"{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": null}",
				"{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": \"\"}",
				"{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": \"NO_EXISTE\"}",
				"{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": 300}",
				"{\"transactionTypeCode\": \"\", \"monthlyAmountRangeCode\": \"HASTA_200\"}",
				"{\"transactionTypeCode\": \"NO_EXISTE\", \"monthlyAmountRangeCode\": \"HASTA_200\"}",
				// the free amount of the previous contract is not accepted any more (VDI-24: no free text)
				"{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountUsd\": 320}", "" };
		for (String body : bodies) {
			mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id)).andExpect(status().isNotFound());
	}

	@Test
	void correctingTheRangeMovesTheScoreBothWays() throws Exception {
		String id = startRequest();
		registerRange(id, "PAGO_SALARIO", "HASTA_200");
		registerRange(id, "PAGO_SALARIO", "MAS_1000");
		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id)).andExpect(jsonPath("$.score").value(16));
		registerRange(id, "PAGO_SALARIO", "200_500");
		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id)).andExpect(jsonPath("$.score").value(3));
		mvc.perform(get("/api/onboarding/requests/{id}", id)).andExpect(jsonPath("$.riskLevel").value("LOW"));
	}

	@Test
	void theRangesOfTheModelInForceSetTheLevel() throws Exception {
		// Model v3 with narrower ranges: low up to 2, medium up to 5, high above; v2 stops being in force
		publishModel(2, 5);
		assertLevel("HASTA_200", "LOW", 0);
		assertLevel("200_500", "MEDIUM", 3);
		assertLevel("500_1000", "HIGH", 6);

		String id = startRequest();
		registerRange(id, "PAGO_SALARIO", "500_1000");
		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id))
			.andExpect(jsonPath("$.modelVersion").value(3))
			.andExpect(jsonPath("$.explanation").value(
					"Score 6 de 100, riesgo alto (Modelo de score v3): suman sobre todo monto declarado (6)."));
	}

	@Test
	void withoutAModelInForceTheRequestCannotBeScored() throws Exception {
		jdbc.update("update score_model set valid_to = now() + interval '1 second' where valid_to is null");
		jdbc.update("update score_model set valid_to = now() where valid_to is not null");
		String id = startRequest();

		registerRange(id, "PAGO_SALARIO", "HASTA_200").andExpect(status().isConflict());
	}

	private void assertScore(String rangeCode, int score, double evaluatedValue) throws Exception {
		String id = startRequest();
		registerRange(id, "PAGO_SALARIO", rangeCode).andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("LOW"))
			.andExpect(jsonPath("$.score").value(score))
			.andExpect(jsonPath("$.evaluatedValue").value(evaluatedValue));
	}

	private void assertLevel(String rangeCode, String level, int score) throws Exception {
		String id = startRequest();
		registerRange(id, "PAGO_SALARIO", rangeCode).andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value(level))
			.andExpect(jsonPath("$.score").value(score));
		mvc.perform(get("/api/onboarding/requests/{id}", id)).andExpect(jsonPath("$.riskLevel").value(level));
	}

	/** Replaces model v2 with a v3 that has the same weights and these ranges. */
	private void publishModel(int lowMax, int mediumMax) {
		jdbc.update("update score_model set valid_to = now() where version = 2");
		jdbc.update("insert into score_model (version, name, low_max, medium_max, valid_from)"
				+ " values (3, 'Modelo de score v3', ?, ?, now())", lowMax, mediumMax);
		jdbc.update("insert into score_model_weight (model_version, signal, max_points)"
				+ " select 3, signal, max_points from score_model_weight where model_version = 2");
	}

	private ResultActions registerRange(String id, String type, String rangeCode) throws Exception {
		return mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"transactionTypeCode\": \"" + type + "\", \"monthlyAmountRangeCode\": \"" + rangeCode + "\"}"));
	}

	private String startRequest() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

}
