package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-122: the customer gets a unique, permanent number (CLI-0000001) when their first request is submitted. They keep
 * it with later requests, it shows in the console, and the risk signals can be found with it.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CustomerNumberApiTests {

	private static final String NUMBER_FORMAT = "CLI-[0-9]{7,}";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	EntityManager em;

	MockMvc app;

	@BeforeEach
	void signIn() {
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void theCustomerHasNoNumberUntilTheyFirstSubmit() throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "02345678-9", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4001"}
				""")).andExpect(status().isOk());

		perform(get("/api/console/requests/{id}", requestId)).andExpect(jsonPath("$.customerNumber").doesNotExist());
		perform(get("/api/console/customers").param("dui", "02345678-9"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.number").doesNotExist());
	}

	@Test
	void submittingGivesTheCustomerANumberThatTheConsoleShows() throws Exception {
		String requestId = idOf(submitted("03456789-0", "7123-4002", "200_500"));

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.customerNumber").value(matchesPattern(NUMBER_FORMAT)));
		perform(get("/api/console/customers").param("dui", "03456789-0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.number").value(matchesPattern(NUMBER_FORMAT)));
	}

	@Test
	void theCustomerKeepsTheirNumberWithAnotherRequest() throws Exception {
		String first = idOf(submitted("04567891-2", "7123-4003", "200_500"));
		String number = numberOfRequest(first);

		String second = idOf(submitted("04567891-2", "7123-4003", "200_500"));

		assertThat(second).isNotEqualTo(first);
		assertThat(numberOfRequest(second)).isEqualTo(number);
	}

	@Test
	void differentCustomersGetDifferentNumbers() throws Exception {
		String first = numberOfRequest(idOf(submitted("05678912-3", "7123-4004", "200_500")));
		String second = numberOfRequest(idOf(submitted("06789123-4", "7123-4005", "200_500")));

		assertThat(first).isNotEqualTo(second);
		assertThat(Long.parseLong(second.substring(4))).isGreaterThan(Long.parseLong(first.substring(4)));
	}

	@Test
	void theRiskSignalsOfTheCustomerAreFoundByTheirNumber() throws Exception {
		String requestId = idOf(submitted("07891234-5", "7123-4006", "200_500"));
		String number = numberOfRequest(requestId);

		perform(get("/api/console/customers/risk-history").param("number", number))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.customerNumber").value(number))
			.andExpect(jsonPath("$.requests.length()").value(1))
			.andExpect(jsonPath("$.requests[0].requestId").value(requestId))
			.andExpect(jsonPath("$.requests[0].status").value("COMPLETED"))
			.andExpect(jsonPath("$.requests[0].assessments[*].score").isNotEmpty())
			.andExpect(jsonPath("$.requests[0].assessments[*].level").value(hasItem("LOW")))
			.andExpect(jsonPath("$.requests[0].decisions.length()").value(1))
			.andExpect(jsonPath("$.requests[0].decisions[0].decision").value("AUTOMATIC_FLOW"));
	}

	@Test
	void theRiskHistoryGroupsTheSignalsByRequest() throws Exception {
		String first = idOf(submitted("08912345-6", "7123-4007", "200_500"));
		String second = idOf(submitted("08912345-6", "7123-4007", "500_1000"));
		String number = numberOfRequest(first);

		perform(get("/api/console/customers/risk-history").param("number", number))
			.andExpect(jsonPath("$.requests.length()").value(2))
			.andExpect(jsonPath("$.requests[0].requestId").value(first))
			.andExpect(jsonPath("$.requests[1].requestId").value(second))
			.andExpect(jsonPath("$.requests[0].decisions.length()").value(1))
			.andExpect(jsonPath("$.requests[1].decisions.length()").value(1));
	}

	@Test
	void anUnknownCustomerNumberIsNotFound() throws Exception {
		perform(get("/api/console/customers/risk-history").param("number", "CLI-9999999"))
			.andExpect(status().isNotFound());
	}

	@Test
	void aMalformedCustomerNumberIsRejected() throws Exception {
		perform(get("/api/console/customers/risk-history").param("number", "CLI-12")).andExpect(status().isBadRequest());
		perform(get("/api/console/customers/risk-history").param("number", "x' or 1=1")).andExpect(status().isBadRequest());
	}

	private String numberOfRequest(String requestId) throws Exception {
		String body = perform(get("/api/console/requests/{id}", requestId)).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.customerNumber");
	}

	/** The whole mobile flow up to submit, with the given DUI, phone and monthly amount range. */
	private ResultActions submitted(String dui, String phone, String rangeCode) throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(jsonPath("$.completedSteps").value(1));
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "%s", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "%s"}
				""".formatted(dui, phone))).andExpect(jsonPath("$.completedSteps").value(2));
		DuiStep.complete(app, requestId, dui, "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "%s"}
				""".formatted(rangeCode))).andExpect(status().isOk());
		ResultActions submit = perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isOk());
		// The number is written straight to the database. In production every call is its own transaction; here the
		// whole test is one, so the customer already loaded would still show no number: read it again.
		em.flush();
		em.clear();
		return submit;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return app.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
