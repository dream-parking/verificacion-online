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

/** VDI-23, VDI-24, VDI-25: income and expected activity are chosen from the catalogs; a low range scores low risk. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class OnboardingApiTests {

	@Autowired
	MockMvc mvc;

	@Test
	void lowMonthlyRangeScoresLowRisk() throws Exception {
		String id = startRequest();

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("LOW"))
			.andExpect(jsonPath("$.ruleCode").value("R-01"))
			.andExpect(jsonPath("$.explanation")
				.value("Monto mensual declarado en el rango «USD 200.01 a 500», que no supera el umbral de USD 500, riesgo bajo."));

		mvc.perform(get("/api/onboarding/requests/{id}", id))
			.andExpect(jsonPath("$.riskLevel").value("LOW"))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void rangeAboveTheThresholdStaysPendingAndReplacesPreviousScore() throws Exception {
		String id = startRequest();
		registerRange(id, "HASTA_200");

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "AHORRO", "monthlyAmountRangeCode": "500_1000"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.ruleCode").isEmpty());

		mvc.perform(get("/api/onboarding/requests/{id}/risk-assessment", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.level").value("PENDING_REVIEW"))
			.andExpect(jsonPath("$.evaluatedValue").value(1000));
	}

	@Test
	void declaresIncome() throws Exception {
		String id = startRequest();

		mvc.perform(put("/api/onboarding/requests/{id}/income", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
					"""))
			.andExpect(status().isNoContent());

		mvc.perform(put("/api/onboarding/requests/{id}/income", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"sourceCode": "OTRO", "rangeCode": "HASTA_500"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsInvalidInput() throws Exception {
		String id = startRequest();

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "NO_EXISTE"}
					"""))
			.andExpect(status().isBadRequest());

		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "NO_EXISTE", "monthlyAmountRangeCode": "HASTA_200"}
					"""))
			.andExpect(status().isBadRequest());

		mvc.perform(get("/api/onboarding/requests/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void submittedRequestCannotChange() throws Exception {
		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", EntityMappingTests.DEMO_REQUEST)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "HASTA_200"}
					"""))
			.andExpect(status().isConflict());
	}

	@Test
	void listsCatalogs() throws Exception {
		mvc.perform(get("/api/catalogs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.incomeSources.length()").value(5))
			.andExpect(jsonPath("$.incomeRanges.length()").value(4))
			.andExpect(jsonPath("$.incomeRanges[0].code").value("HASTA_500"))
			.andExpect(jsonPath("$.incomeRanges[3].code").value("MAS_2500"))
			.andExpect(jsonPath("$.monthlyAmountRanges.length()").value(4))
			.andExpect(jsonPath("$.monthlyAmountRanges[0].code").value("HASTA_200"))
			.andExpect(jsonPath("$.monthlyAmountRanges[1].minUsd").value(200.01))
			.andExpect(jsonPath("$.monthlyAmountRanges[3].maxUsd").isEmpty())
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

	private void registerRange(String id, String rangeCode) throws Exception {
		mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": \"" + rangeCode + "\"}"))
			.andExpect(status().isOk());
	}

}
