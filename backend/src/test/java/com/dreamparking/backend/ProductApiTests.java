package com.dreamparking.backend;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
 * VDI-146: an approved request gets the active products with their conditions; one that is not approved gets the state
 * error, not an empty list.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProductApiTests {

	private static final String DUI = "01234567-8";

	/** The simulated RNPN does not answer for this DUI, so the request waits. */
	private static final String UNAVAILABLE_RNPN_DUI = "00000030-1";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	MockMvc app;

	@BeforeEach
	void signIn() {
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void anApprovedRequestGetsTheActiveProductsWithTheirConditions() throws Exception {
		String requestId = approved(DUI);

		perform(get("/api/onboarding/products").param("requestId", requestId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].code").value("AHORRO_BASICA"))
			.andExpect(jsonPath("$[0].name").value("Cuenta de ahorro básica"))
			.andExpect(jsonPath("$[0].monthlyFeeUsd").value(0.00))
			.andExpect(jsonPath("$[0].minimumOpeningDepositUsd").value(10.00))
			.andExpect(jsonPath("$[0].annualInterestRate").value(0.50))
			.andExpect(jsonPath("$[1].code").value("AHORRO_PLUS"))
			.andExpect(jsonPath("$[2].code").value("CORRIENTE"));
	}

	@Test
	void aProductSwitchedOffIsNotOffered() throws Exception {
		String requestId = approved(DUI);
		jdbc.update("update product set active = false where code = 'AHORRO_PLUS'");

		perform(get("/api/onboarding/products").param("requestId", requestId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[?(@.code == 'AHORRO_PLUS')]").isEmpty());
	}

	@Test
	void aRequestInProgressGetsTheStateErrorNotAnEmptyList() throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));

		perform(get("/api/onboarding/products").param("requestId", requestId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("Only submitted and approved requests")));
	}

	@Test
	void anAbandonedRequestGetsTheStateError() throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(post("/api/onboarding/requests/{id}/abandon", requestId)).andExpect(status().isOk());

		perform(get("/api/onboarding/products").param("requestId", requestId)).andExpect(status().isConflict());
	}

	@Test
	void aRequestWaitingForTheRnpnGetsTheStateError() throws Exception {
		String requestId = idOf(submitted(UNAVAILABLE_RNPN_DUI, "200_500"));

		perform(get("/api/onboarding/products").param("requestId", requestId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("waiting for the RNPN")));
	}

	@Test
	void aRequestThatDoesNotExistIsNotFound() throws Exception {
		perform(get("/api/onboarding/products").param("requestId", UUID.randomUUID().toString()))
			.andExpect(status().isNotFound());
	}

	@Test
	void theRequestIdIsRequiredAndMustBeAUuid() throws Exception {
		perform(get("/api/onboarding/products")).andExpect(status().isBadRequest());
		perform(get("/api/onboarding/products").param("requestId", "not-a-uuid")).andExpect(status().isBadRequest());
	}

	private String approved(String dui) throws Exception {
		return idOf(submitted(dui, "200_500").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")));
	}

	/** The whole mobile flow up to submit, with the given DUI and monthly amount range. */
	private ResultActions submitted(String dui, String rangeCode) throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/signals", requestId).content("""
				{"deviceFingerprint": "d4f19a3ce7b2", "deviceModel": "Pixel 8", "operatingSystem": "Android"}
				""")).andExpect(status().is2xxSuccessful());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "%s", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""".formatted(dui))).andExpect(status().isOk());
		DuiStep.complete(app, requestId, dui, "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "%s"}
				""".formatted(rangeCode))).andExpect(status().isOk());
		return perform(post("/api/onboarding/requests/{id}/submit", requestId));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return app.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
