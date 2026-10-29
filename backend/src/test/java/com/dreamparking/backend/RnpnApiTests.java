package com.dreamparking.backend;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
 * VDI-111: when a request is submitted the confirmed DUI data is checked against the RNPN (simulated). The three
 * results are stored in the file, they add to the score without deciding anything, and a request whose RNPN does not
 * answer waits instead of being approved automatically.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class RnpnApiTests {

	/** The simulated RNPN does not match the data of this DUI. */
	private static final String NO_MATCH_DUI = "00000020-4";

	/** The simulated RNPN does not answer for this DUI. */
	private static final String UNAVAILABLE_DUI = "00000030-1";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	MockMvc app;

	@BeforeEach
	void signIn() {
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void aDuiThatMatchesIsRecordedAndTheRequestContinues() throws Exception {
		String requestId = idOf(submitted("01234567-8", "200_500")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED")));

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.rnpn.outcome").value("MATCH"))
			.andExpect(jsonPath("$.rnpn.simulated").value(true))
			.andExpect(jsonPath("$.rnpn.provider").value("simulated"))
			.andExpect(jsonPath("$.rnpn.attempts").value(1))
			.andExpect(jsonPath("$.rnpn.mismatchedFields.length()").value(0))
			.andExpect(jsonPath("$.rnpn.checkedAt").isNotEmpty())
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("RNPN_CHECKED")))
			.andExpect(jsonPath("$.risk.signals[?(@.signal == 'RNPN')].points").value(0))
			.andExpect(jsonPath("$.risk.signals[?(@.signal == 'RNPN')].maxPoints").value(15));
	}

	@Test
	void aDuiThatDoesNotMatchIsRecordedWithTheFieldsAndAddsAllThePointsWithoutStoppingTheRequest() throws Exception {
		String requestId = idOf(submitted(NO_MATCH_DUI, "200_500")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED")));

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.rnpn.outcome").value("NO_MATCH"))
			.andExpect(jsonPath("$.rnpn.attempts").value(1))
			.andExpect(jsonPath("$.rnpn.mismatchedFields[0]").value("LAST_NAMES"))
			.andExpect(jsonPath("$.rnpn.mismatchedFields[1]").value("BIRTH_DATE"))
			.andExpect(jsonPath("$.risk.signals[?(@.signal == 'RNPN')].points").value(15))
			.andExpect(jsonPath("$.risk.signals[?(@.signal == 'RNPN')].observation")
				.value(hasItem(containsString("no coinciden (apellidos, fecha de nacimiento)"))));
	}

	@Test
	void anRnpnThatDoesNotAnswerLeavesTheRequestWaitingAndItCannotOpenAnAccount() throws Exception {
		String requestId = idOf(submitted(UNAVAILABLE_DUI, "200_500")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("AWAITING_RNPN"))
			.andExpect(jsonPath("$.number").isNotEmpty()));

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.status").value("AWAITING_RNPN"))
			.andExpect(jsonPath("$.rnpn.outcome").value("UNAVAILABLE"))
			.andExpect(jsonPath("$.rnpn.attempts").value(3))
			.andExpect(jsonPath("$.rnpn.mismatchedFields.length()").value(0))
			.andExpect(jsonPath("$.risk.signals[?(@.signal == 'RNPN')].points").value(8))
			.andExpect(jsonPath("$.timeline[*].description")
				.value(hasItem("Cotejo con el RNPN no hecho: el RNPN no respondió")));
		perform(post("/api/console/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_wait_1234", "lastFour": "1234"}
				""".formatted(requestId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("waiting for the RNPN")));
	}

	@Test
	void theConsoleListFiltersTheRequestsThatAreWaiting() throws Exception {
		String requestId = idOf(submitted(UNAVAILABLE_DUI, "200_500"));
		// The list reads with plain JDBC: write the pending status change first (a real request commits it).
		em.flush();

		perform(get("/api/console/requests").param("status", "AWAITING_RNPN"))
			.andExpect(jsonPath("$.content[*].id").value(hasItem(requestId)));
		perform(get("/api/console/requests").param("status", "COMPLETED"))
			.andExpect(jsonPath("$.content[*].id").value(not(hasItem(requestId))));
	}

	@Test
	void aRequestThatWaitsForTheRnpnKeepsThatStatusEvenWhenItsScoreIsHigh() throws Exception {
		strictModelInForce();
		String requestId = idOf(submitted(UNAVAILABLE_DUI, "MAS_1000")
			.andExpect(jsonPath("$.status").value("AWAITING_RNPN")));

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.status").value("AWAITING_RNPN"))
			.andExpect(jsonPath("$.riskDecisions[0].decision").value("ENHANCED_REVIEW"))
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("ENHANCED_REVIEW_REQUIRED")));
	}

	@Test
	void theCheckIsKeptInTheFileOnceAndNeverChanges() throws Exception {
		String requestId = idOf(submitted(NO_MATCH_DUI, "200_500"));
		em.flush();

		assertThat(jdbc.queryForObject("select count(*) from rnpn_check where request_id = ?::uuid", Integer.class,
				requestId))
			.isEqualTo(1);
		assertThatThrownBy(() -> jdbc.update("update rnpn_check set outcome = 'MATCH', mismatched_fields = '{}' "
				+ "where request_id = ?::uuid", requestId))
			.hasMessageContaining("cannot be modified or deleted");
	}

	/** A model in force whose threshold is 2, so any request with an amount above 200 goes over it. */
	private void strictModelInForce() {
		jdbc.update("update score_model set valid_to = now() where valid_to is null");
		jdbc.update("""
				insert into score_model (version, name, low_max, medium_max, valid_from)
				values (99, 'Strict test model', 1, 2, now())""");
		jdbc.update("insert into score_model_weight (model_version, signal, max_points) values (99, 'AMOUNT', 25)");
	}

	/** The whole mobile flow up to submit, with the given DUI and monthly amount range. */
	private ResultActions submitted(String dui, String rangeCode) throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(jsonPath("$.completedSteps").value(1));
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "%s", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""".formatted(dui))).andExpect(jsonPath("$.completedSteps").value(2));
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
