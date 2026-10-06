package com.dreamparking.backend;

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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** VDI-25: a request that declares a small monthly amount gets a low risk score. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class OnboardingApiTests {

	@Autowired
	MockMvc mvc;

	@Test
	void smallMonthlyAmountScoresLowRisk() throws Exception {
		String id = startRequest();

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountUsd": 320}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("LOW"))
			.andExpect(jsonPath("$.ruleCode").value("R-01"))
			.andExpect(jsonPath("$.explanation")
				.value("Monto mensual declarado de USD 320, menor al umbral de USD 500, riesgo bajo."));

		mvc.perform(get("/api/onboarding/requests/{id}", id))
			.andExpect(jsonPath("$.riskLevel").value("LOW"))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void amountAtThresholdStaysPendingAndReplacesPreviousScore() throws Exception {
		String id = startRequest();
		registerAmount(id, "120");

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "AHORRO", "monthlyAmountUsd": 500}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.ruleCode").isEmpty());

		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.evaluatedValue").value(500));
	}

	@Test
	void declaresIncome() throws Exception {
		String id = startRequest();

		mvc.perform(put("/api/onboarding/requests/{id}/income", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceCode": "SALARIO", "rangeCode": "MENOS_500"}
					"""))
			.andExpect(status().isNoContent());

		mvc.perform(put("/api/onboarding/requests/{id}/income", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceCode": "OTRO", "rangeCode": "MENOS_500"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidInput() throws Exception {
		String id = startRequest();

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountUsd": -1}
					"""))
			.andExpect(status().isBadRequest());

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "NO_EXISTE", "monthlyAmountUsd": 100}
					"""))
			.andExpect(status().isBadRequest());

		mvc.perform(get("/api/onboarding/requests/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void submittedRequestCannotChange() throws Exception {
		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", EntityMappingTests.DEMO_REQUEST)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountUsd": 100}
					"""))
			.andExpect(status().isConflict());
	}

	@Test
	void listsCatalogs() throws Exception {
		mvc.perform(get("/api/catalogs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.incomeSources.length()").value(5))
			.andExpect(jsonPath("$.incomeRanges[0].code").value("MENOS_500"))
			.andExpect(jsonPath("$.transactionTypes[0].code").value("PAGO_SALARIO"));
	}

	private String startRequest() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.riskLevel").value("NOT_EVALUATED"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private void registerAmount(String id, String amount) throws Exception {
		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountUsd\": " + amount + "}"))
			.andExpect(status().isOk());
	}

}
