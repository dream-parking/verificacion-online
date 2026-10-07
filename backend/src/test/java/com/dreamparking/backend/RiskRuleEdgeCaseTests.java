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
 * VDI-58: edge cases of rule R-01. The range that ends exactly at the threshold (USD 500) is low risk; the next
 * one up and the open-ended one stay pending; no data means not evaluated; invalid input leaves no score.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RiskRuleEdgeCaseTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	OnboardingRequestRepository requests;

	@Autowired
	RiskAssessmentService riskAssessments;

	@Test
	void rangesThatEndAtOrBelowTheThresholdAreLowRisk() throws Exception {
		assertScore("HASTA_200", "LOW", "R-01", 200);
		assertScore("200_500", "LOW", "R-01", 500);
	}

	@Test
	void rangesAboveTheThresholdStayPending() throws Exception {
		assertScore("500_1000", "PENDING_REVIEW", null, 1000);
		assertScore("MAS_1000", "PENDING_REVIEW", null, 1000.01);
	}

	@Test
	void theAppliedRuleKeepsItsThresholdForTheExplanation() throws Exception {
		String id = startRequest();
		registerRange(id, "PAGO_SALARIO", "200_500");

		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id))
			.andExpect(jsonPath("$.ruleCode").value("R-01"))
			.andExpect(jsonPath("$.ruleName").value("Monto mensual bajo"))
			.andExpect(jsonPath("$.ruleThreshold").value(500))
			.andExpect(jsonPath("$.evaluatedValue").value(500));
	}

	@Test
	void withoutARangeTheRequestIsNotEvaluated() throws Exception {
		String id = startRequest();

		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id)).andExpect(status().isNotFound());

		OnboardingRequest request = requests.findById(UUID.fromString(id)).orElseThrow();
		RiskAssessmentResponse response = riskAssessments.evaluate(request, null);
		assertThat(response.level()).isEqualTo(RiskLevel.NOT_EVALUATED);
		assertThat(response.ruleCode()).isNull();
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
		registerRange(id, "PAGO_SALARIO", "500_1000");
		mvc.perform(get("/api/onboarding/requests/{id}", id)).andExpect(jsonPath("$.riskLevel").value("PENDING_REVIEW"));
		registerRange(id, "PAGO_SALARIO", "200_500");
		mvc.perform(get("/api/onboarding/requests/{id}", id)).andExpect(jsonPath("$.riskLevel").value("LOW"));
	}

	private void assertScore(String rangeCode, String level, String ruleCode, double evaluatedValue) throws Exception {
		String id = startRequest();
		ResultActions result = registerRange(id, "PAGO_SALARIO", rangeCode).andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value(level))
			.andExpect(jsonPath("$.evaluatedValue").value(evaluatedValue));
		if (ruleCode == null) {
			result.andExpect(jsonPath("$.ruleCode").isEmpty());
		}
		else {
			result.andExpect(jsonPath("$.ruleCode").value(ruleCode));
		}
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
