package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
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
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.repository.OnboardingRequestRepository;
import com.dreamparking.backend.risk.service.RiskDecisionService;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-117: a request whose score goes above the threshold of the score model is held for enhanced review instead of
 * being approved automatically, and every decision is kept in the audit log with its reason, date and time.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EnhancedReviewApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	@Autowired
	OnboardingRequestRepository requests;

	@Autowired
	RiskDecisionService decisions;

	MockMvc app;

	@BeforeEach
	void signIn() {
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void aScoreAboveTheThresholdHoldsTheRequestForEnhancedReview() throws Exception {
		strictModelInForce();
		String requestId = idOf(submitted("MAS_1000").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ENHANCED_REVIEW")));

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.status").value("ENHANCED_REVIEW"))
			.andExpect(jsonPath("$.riskDecisions.length()").value(1))
			.andExpect(jsonPath("$.riskDecisions[0].decision").value("ENHANCED_REVIEW"))
			.andExpect(jsonPath("$.riskDecisions[0].threshold").value(2))
			.andExpect(jsonPath("$.riskDecisions[0].modelVersion").value(2))
			.andExpect(jsonPath("$.riskDecisions[0].score").value(greaterThan(2)))
			.andExpect(jsonPath("$.riskDecisions[0].reason").value(containsString("above the enhanced-review threshold of 2")))
			.andExpect(jsonPath("$.riskDecisions[0].reason").value(containsString("main signals: amount")))
			.andExpect(jsonPath("$.riskDecisions[0].decidedBy").value("SYSTEM"))
			.andExpect(jsonPath("$.riskDecisions[0].decidedAt").isNotEmpty())
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("ENHANCED_REVIEW_REQUIRED")));
	}

	@Test
	void aRequestHeldForEnhancedReviewCannotOpenAnAccountNorBeFilteredAsCompleted() throws Exception {
		strictModelInForce();
		String requestId = idOf(submitted("MAS_1000"));

		perform(post("/api/console/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_held_1234", "lastFour": "1234"}
				""".formatted(requestId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("enhanced review")));
		// The console list reads with plain JDBC: write the pending status change first (a real request commits it).
		em.flush();
		perform(get("/api/console/requests").param("status", "ENHANCED_REVIEW"))
			.andExpect(jsonPath("$.content[*].id").value(hasItem(requestId)));
		perform(get("/api/console/requests").param("status", "COMPLETED"))
			.andExpect(jsonPath("$.content[*].id").value(not(hasItem(requestId))));
	}

	@Test
	void aScoreWithinTheThresholdContinuesAndTheDecisionIsLoggedToo() throws Exception {
		String requestId = idOf(submitted("200_500").andExpect(jsonPath("$.status").value("COMPLETED")));

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.riskDecisions.length()").value(1))
			.andExpect(jsonPath("$.riskDecisions[0].decision").value("AUTOMATIC_FLOW"))
			.andExpect(jsonPath("$.riskDecisions[0].threshold").value(69))
			.andExpect(jsonPath("$.riskDecisions[0].modelVersion").value(1))
			.andExpect(jsonPath("$.riskDecisions[0].reason").value(containsString("not above the enhanced-review threshold of 69")))
			.andExpect(jsonPath("$.timeline[*].type").value(not(hasItem("ENHANCED_REVIEW_REQUIRED"))));
		perform(post("/api/console/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_free_1234", "lastFour": "1234"}
				""".formatted(requestId)))
			.andExpect(status().isCreated());
	}

	@Test
	void aRequestWithoutScoreIsNeitherHeldNorLogged() {
		OnboardingRequest request = requests.save(new OnboardingRequest());

		assertThat(decisions.decideOnSubmission(request)).isEmpty();
		assertThat(decisions.log(request.getId())).isEmpty();
	}

	/** A model in force whose threshold is 2, so any request with an amount above 200 goes over it. */
	private void strictModelInForce() {
		jdbc.update("update score_model set valid_to = now() where valid_to is null");
		jdbc.update("""
				insert into score_model (version, name, low_max, medium_max, valid_from)
				values (2, 'Strict test model', 1, 2, now())""");
		jdbc.update("insert into score_model_weight (model_version, signal, max_points) values (2, 'AMOUNT', 25)");
	}

	/** The whole mobile flow up to submit, declaring the given monthly amount range. */
	private ResultActions submitted(String rangeCode) throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(jsonPath("$.completedSteps").value(1));
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""")).andExpect(jsonPath("$.completedSteps").value(2));
		DuiStep.complete(app, requestId, "01234567-8", "Ana Sofía", "Pérez López");
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
