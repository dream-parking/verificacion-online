package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
 * VDI-3: the customer declares how much and what type of money will move (step 5), and the movements the core reports
 * for the account are compared month by month with that declaration, raising an alert the first time each deviation
 * shows up.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProfileTrackingApiTests {

	/** Salary and remittances, USD 200.01–500 (about 350) in 2 deposits, and about USD 300 of outflow a month. */
	private static final String FULL_PROFILE = """
			{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500",
			 "additionalTransactionTypes": [{"code": "REMESAS"}], "monthlyAmountUsd": 350.00,
			 "monthlyTransactionCount": 2, "monthlyOutflowUsd": 300.00}
			""";

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
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.KYC_LEAD));
	}

	@Test
	void theFullDeclaredProfileIsShownInTheConsole() throws Exception {
		String requestId = submittedRequest(FULL_PROFILE);

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.expectedActivity.transactionTypeCode").value("PAGO_SALARIO"))
			.andExpect(jsonPath("$.expectedActivity.additionalTransactionTypeCodes", contains("REMESAS")))
			.andExpect(jsonPath("$.expectedActivity.monthlyAmountUsd").value(350.00))
			.andExpect(jsonPath("$.expectedActivity.monthlyTransactionCount").value(2))
			.andExpect(jsonPath("$.expectedActivity.monthlyOutflowUsd").value(300.00));
	}

	@Test
	void theOldBodyWithOnlyTypeAndRangeIsStillAccepted() throws Exception {
		String requestId = submittedRequest("""
				{"transactionTypeCode": "AHORRO", "monthlyAmountRangeCode": "HASTA_200"}
				""");

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.expectedActivity.additionalTransactionTypeCodes", empty()))
			.andExpect(jsonPath("$.expectedActivity.monthlyAmountUsd").doesNotExist());
	}

	@Test
	void theEstimatedAmountMustBeInsideTheChosenRange() throws Exception {
		String requestId = requestAtExpectedActivity();
		expectedActivity(requestId, """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500", "monthlyAmountUsd": 800}
				""")
			.andExpect(status().isBadRequest());
		expectedActivity(requestId, """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "MAS_1000", "monthlyAmountUsd": 25000}
				""")
			.andExpect(status().isOk());
	}

	@Test
	void extraTypesMustBeKnownAndNotRepeated() throws Exception {
		String requestId = requestAtExpectedActivity();
		expectedActivity(requestId, """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500",
				 "additionalTransactionTypes": [{"code": "PAGO_SALARIO"}]}
				""")
			.andExpect(status().isBadRequest());
		expectedActivity(requestId, """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500",
				 "additionalTransactionTypes": [{"code": "REMESAS"}, {"code": "REMESAS"}]}
				""")
			.andExpect(status().isBadRequest());
		expectedActivity(requestId, """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500",
				 "additionalTransactionTypes": [{"code": "LOTERIA"}]}
				""")
			.andExpect(status().isBadRequest());
		expectedActivity(requestId, """
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500", "monthlyTransactionCount": 0}
				""")
			.andExpect(status().isBadRequest());
	}

	@Test
	void activityInsideTheProfileRaisesNoAlert() throws Exception {
		String accountId = openAccount(submittedRequest(FULL_PROFILE));

		movement(accountId, "m-1", "IN", "300.00", "PAGO_SALARIO", "2025-03-05T15:00:00Z")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.movement.externalId").value("m-1"))
			.andExpect(jsonPath("$.newDeviations", empty()));

		perform(get("/api/console/accounts/{id}/profile-comparison", accountId).param("month", "2025-03"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.month").value("2025-03"))
			.andExpect(jsonPath("$.tolerancePercent").value(20))
			.andExpect(jsonPath("$.declared.transactionTypeCodes", contains("PAGO_SALARIO", "REMESAS")))
			.andExpect(jsonPath("$.declared.rangeMaxUsd").value(500.00))
			.andExpect(jsonPath("$.actual.inflowUsd").value(300.00))
			.andExpect(jsonPath("$.actual.inflowCount").value(1))
			.andExpect(jsonPath("$.deviations", empty()))
			.andExpect(jsonPath("$.withinProfile").value(true));
	}

	@Test
	void eachDeviationFromTheProfileRaisesOneAlertPerMonth() throws Exception {
		String accountId = openAccount(submittedRequest(FULL_PROFILE));

		// 300: inside everything
		movement(accountId, "m-1", "IN", "300.00", "PAGO_SALARIO", "2025-03-05T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations", empty()));
		// 500 received: above the declared 350 + 20 % (420), still inside the range + 20 % (600)
		String alertId = read(movement(accountId, "m-2", "IN", "200.00", "REMESAS", "2025-03-08T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type", contains("INFLOW_ABOVE_DECLARED_AMOUNT")))
			.andExpect(jsonPath("$.newDeviations[0].declaredValue").value(350.00))
			.andExpect(jsonPath("$.newDeviations[0].actualValue").value(500.00))
			.andExpect(jsonPath("$.newDeviations[0].limitValue").value(420.00)), "$.newDeviations[0].alertId");
		// third deposit (2 declared) and of a type not declared
		movement(accountId, "m-3", "IN", "100.00", "COBROS_NEGOCIO", "2025-03-12T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type",
					contains("MORE_TRANSACTIONS_THAN_DECLARED", "UNDECLARED_TRANSACTION_TYPE")))
			.andExpect(jsonPath("$.newDeviations[1].alertTypeCode").value("ORIGEN_DISTINTO"));
		// 650 received: above the range; the deviations already alerted are not raised again
		movement(accountId, "m-4", "IN", "50.00", null, "2025-03-20T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type", contains("INFLOW_ABOVE_RANGE")));
		// 400 out: above the declared 300 + 20 %
		movement(accountId, "m-5", "OUT", "400.00", null, "2025-03-25T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type", contains("OUTFLOW_ABOVE_DECLARED")));
		// a retry of the core does not count twice
		movement(accountId, "m-5", "OUT", "400.00", null, "2025-03-25T15:00:00Z").andExpect(status().isConflict());

		perform(get("/api/console/accounts/{id}/profile-comparison", accountId).param("month", "2025-03"))
			.andExpect(jsonPath("$.withinProfile").value(false))
			.andExpect(jsonPath("$.actual.inflowUsd").value(650.00))
			.andExpect(jsonPath("$.actual.inflowCount").value(4))
			.andExpect(jsonPath("$.actual.outflowUsd").value(400.00))
			.andExpect(jsonPath("$.actual.inflowByTransactionType[?(@.transactionTypeCode == 'COBROS_NEGOCIO')].declared")
				.value(contains(false)))
			.andExpect(jsonPath("$.deviations[*].type", containsInAnyOrder("INFLOW_ABOVE_RANGE",
					"INFLOW_ABOVE_DECLARED_AMOUNT", "MORE_TRANSACTIONS_THAN_DECLARED", "OUTFLOW_ABOVE_DECLARED",
					"UNDECLARED_TRANSACTION_TYPE")))
			.andExpect(jsonPath("$.deviations[*].alertId", everyItem(notNullValue())));

		perform(get("/api/console/alerts/{id}", alertId))
			.andExpect(jsonPath("$.typeCode").value("PERFIL_EXCEDIDO"))
			.andExpect(jsonPath("$.status").value("UNASSIGNED"))
			.andExpect(jsonPath("$.evidence.deviation").value("INFLOW_ABOVE_DECLARED_AMOUNT"))
			.andExpect(jsonPath("$.evidence.month").value("2025-03"));
		perform(get("/api/console/accounts/{id}/movements", accountId).param("month", "2025-03"))
			.andExpect(jsonPath("$.length()").value(5));
	}

	@Test
	void monthsFollowTheElSalvadorCalendar() throws Exception {
		String accountId = openAccount(submittedRequest(FULL_PROFILE));

		// 1 March 05:30 UTC is still 28 February in El Salvador
		movement(accountId, "m-1", "IN", "900.00", "PAGO_SALARIO", "2025-03-01T05:30:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type",
					contains("INFLOW_ABOVE_RANGE", "INFLOW_ABOVE_DECLARED_AMOUNT")));

		perform(get("/api/console/accounts/{id}/profile-comparison", accountId).param("month", "2025-02"))
			.andExpect(jsonPath("$.actual.inflowUsd").value(900.00));
		perform(get("/api/console/accounts/{id}/profile-comparison", accountId).param("month", "2025-03"))
			.andExpect(jsonPath("$.actual.inflowCount").value(0))
			.andExpect(jsonPath("$.withinProfile").value(true));
	}

	@Test
	void invalidMovementsAreRejected() throws Exception {
		String accountId = openAccount(submittedRequest(FULL_PROFILE));

		movement(accountId, "m-1", "IN", "-5.00", null, "2025-03-05T15:00:00Z").andExpect(status().isBadRequest());
		movement(accountId, "m-1", "IN", "5.00", "LOTERIA", "2025-03-05T15:00:00Z").andExpect(status().isBadRequest());
		movement(accountId, "m-1", "IN", "5.00", null, "2999-01-01T00:00:00Z").andExpect(status().isBadRequest());
		perform(get("/api/console/accounts/{id}/profile-comparison", accountId).param("month", "marzo"))
			.andExpect(status().isBadRequest());
		movement(UUID.randomUUID().toString(), "m-1", "IN", "5.00", null, "2025-03-05T15:00:00Z")
			.andExpect(status().isNotFound());

		perform(put("/api/console/accounts/{id}/status", accountId).content("""
				{"status": "CLOSED"}
				"""))
			.andExpect(status().isOk());
		movement(accountId, "m-1", "IN", "5.00", null, "2025-03-05T15:00:00Z").andExpect(status().isConflict());
	}

	@Test
	void movementsCannotBeChanged() throws Exception {
		String accountId = openAccount(submittedRequest(FULL_PROFILE));
		movement(accountId, "m-1", "IN", "300.00", "PAGO_SALARIO", "2025-03-05T15:00:00Z")
			.andExpect(status().isCreated());

		assertThatThrownBy(() -> jdbc.update("update account_movement set amount_usd = 1 where account_id = ?::uuid",
				accountId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theExtraTypesOfASubmittedRequestCannotBeChanged() throws Exception {
		String requestId = submittedRequest(FULL_PROFILE);
		em.flush();

		assertThat(jdbc.queryForObject("select count(*) from expected_activity_extra_type where request_id = ?::uuid",
				Integer.class, requestId)).isEqualTo(1);
		assertThatThrownBy(
				() -> jdbc.update("delete from expected_activity_extra_type where request_id = ?::uuid", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	// ---------------------------------------------------------------- helpers

	/** A request with privacy, basic data and income done: the next step is the expected activity. */
	private String requestAtExpectedActivity() throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()), "$.id");
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				"""))
			.andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				"""))
			.andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				"""))
			.andExpect(status().isNoContent());
		return requestId;
	}

	private String submittedRequest(String expectedActivity) throws Exception {
		String requestId = requestAtExpectedActivity();
		expectedActivity(requestId, expectedActivity).andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isOk());
		return requestId;
	}

	private String openAccount(String requestId) throws Exception {
		String token = "tok_" + requestId.substring(0, 8);
		return read(perform(post("/api/console/accounts").content("""
				{"requestId": "%s", "numberToken": "%s", "lastFour": "4821"}
				""".formatted(requestId, token)))
			.andExpect(status().isCreated()), "$.id");
	}

	private ResultActions expectedActivity(String requestId, String body) throws Exception {
		return perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content(body));
	}

	private ResultActions movement(String accountId, String externalId, String direction, String amount, String type,
			String occurredAt) throws Exception {
		return perform(post("/api/console/accounts/{id}/movements", accountId).content("""
				{"externalId": "%s", "direction": "%s", "amountUsd": %s, "transactionTypeCode": %s, "occurredAt": "%s"}
				""".formatted(externalId, direction, amount, type == null ? "null" : "\"" + type + "\"", occurredAt)));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return app.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
