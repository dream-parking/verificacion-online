package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
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
 * VDI-137: the tolerance of the declared profile tracking is changed from the console without a deploy. A change applies
 * to the next movement, a value outside 0–45 answers 400, the log shows who changed it, when and from what to what, and
 * each deviation keeps the tolerance it was detected with.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProfileToleranceApiTests {

	private static final String TOLERANCE = "/api/console/admin/profile-tolerance";

	/** About USD 350 a month in the range 200.01–500, two deposits: with 20 % the limit of the amount is 420. */
	private static final String PROFILE = """
			{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500", "monthlyAmountUsd": 350.00,
			 "monthlyTransactionCount": 2}
			""";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	MockMvc admin;

	@BeforeEach
	void signIn() {
		admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void theToleranceStartsAt20SetByTheMigration() throws Exception {
		perform(get(TOLERANCE)).andExpect(status().isOk())
			.andExpect(jsonPath("$.tolerancePercent").value(20))
			.andExpect(jsonPath("$.minPercent").value(0))
			.andExpect(jsonPath("$.maxPercent").value(45))
			.andExpect(jsonPath("$.changes.length()").value(1))
			.andExpect(jsonPath("$.changes[0].tolerancePercent").value(20))
			.andExpect(jsonPath("$.changes[0].previousPercent").doesNotExist())
			.andExpect(jsonPath("$.changes[0].changedBy").doesNotExist());
	}

	@Test
	void justAtTheToleranceIsNotADeviationAndAboveItIs() throws Exception {
		String accountId = openAccount(submittedRequest());

		// exactly 350 + 20 %
		movement(accountId, "m-1", "420.00", "2025-03-05T15:00:00Z").andExpect(jsonPath("$.newDeviations", empty()));
		// below
		movement(accountId, "m-2", "419.99", "2025-04-05T15:00:00Z").andExpect(jsonPath("$.newDeviations", empty()));
		// one cent above
		movement(accountId, "m-3", "420.01", "2025-05-05T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type", contains("INFLOW_ABOVE_DECLARED_AMOUNT")))
			.andExpect(jsonPath("$.newDeviations[0].limitValue").value(420.00))
			.andExpect(jsonPath("$.newDeviations[0].tolerancePercent").value(20));
	}

	@Test
	void aChangeAppliesToTheNextMovementWithoutADeploy() throws Exception {
		String accountId = openAccount(submittedRequest());

		change(0).andExpect(status().isOk()).andExpect(jsonPath("$.tolerancePercent").value(0));
		movement(accountId, "m-1", "350.00", "2025-03-05T15:00:00Z").andExpect(jsonPath("$.newDeviations", empty()));
		movement(accountId, "m-2", "350.01", "2025-04-05T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type", contains("INFLOW_ABOVE_DECLARED_AMOUNT")))
			.andExpect(jsonPath("$.newDeviations[0].limitValue").value(350.00))
			.andExpect(jsonPath("$.newDeviations[0].tolerancePercent").value(0));

		change(45).andExpect(status().isOk());
		// 350 + 45 % = 507.50
		movement(accountId, "m-3", "507.50", "2025-05-05T15:00:00Z").andExpect(jsonPath("$.newDeviations", empty()));
		movement(accountId, "m-4", "507.51", "2025-06-05T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[*].type", contains("INFLOW_ABOVE_DECLARED_AMOUNT")))
			.andExpect(jsonPath("$.newDeviations[0].limitValue").value(507.50))
			.andExpect(jsonPath("$.newDeviations[0].tolerancePercent").value(45));
	}

	@Test
	void eachDeviationKeepsTheToleranceItWasDetectedWith() throws Exception {
		String accountId = openAccount(submittedRequest());
		movement(accountId, "m-1", "420.01", "2025-03-05T15:00:00Z")
			.andExpect(jsonPath("$.newDeviations[0].tolerancePercent").value(20));
		String alertId = jdbc.queryForObject("""
				select alert_id::text from profile_deviation where account_id = ?::uuid""", String.class, accountId);

		change(30).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select tolerance_percent from profile_deviation where account_id = ?::uuid",
				Integer.class, accountId))
			.isEqualTo(20);
		perform(get("/api/console/alerts/{id}", alertId)).andExpect(jsonPath("$.evidence.tolerancePercent").value(20));
		// 420.01 is inside 350 + 30 %, but the deviation was recorded with 20 % and raised an alert: it stays.
		perform(get("/api/console/accounts/{id}/profile-comparison", accountId).param("month", "2025-03"))
			.andExpect(jsonPath("$.tolerancePercent").value(30))
			.andExpect(jsonPath("$.withinProfile").value(false))
			.andExpect(jsonPath("$.deviations[*].type", contains("INFLOW_ABOVE_DECLARED_AMOUNT")))
			.andExpect(jsonPath("$.deviations[0].tolerancePercent").value(20))
			.andExpect(jsonPath("$.deviations[0].limitValue").value(420.00))
			.andExpect(jsonPath("$.deviations[0].alertId").value(alertId));

		// Above 30 % too: the same deviation, not a new one, still with the tolerance it was recorded with.
		movement(accountId, "m-2", "100.00", "2025-03-06T15:00:00Z").andExpect(jsonPath("$.newDeviations", empty()));
		perform(get("/api/console/accounts/{id}/profile-comparison", accountId).param("month", "2025-03"))
			.andExpect(jsonPath("$.deviations[*].type", contains("INFLOW_ABOVE_DECLARED_AMOUNT")))
			.andExpect(jsonPath("$.deviations[0].tolerancePercent").value(20))
			.andExpect(jsonPath("$.deviations[0].limitValue").value(420.00))
			.andExpect(jsonPath("$.deviations[0].actualValue").value(520.01));
	}

	@Test
	void theLogShowsWhoChangedItWhenAndFromWhatToWhat() throws Exception {
		change(25).andExpect(status().isOk());
		change(10).andExpect(status().isOk());

		perform(get(TOLERANCE)).andExpect(jsonPath("$.tolerancePercent").value(10))
			.andExpect(jsonPath("$.changes[*].tolerancePercent", contains(10, 25, 20)))
			.andExpect(jsonPath("$.changes[0].previousPercent").value(25))
			.andExpect(jsonPath("$.changes[1].previousPercent").value(20))
			.andExpect(jsonPath("$.changes[0].changedBy").value("Usuario de Prueba"))
			.andExpect(jsonPath("$.changes[0].changedAt", notNullValue()));
		assertThat(jdbc.queryForObject("select count(*) from access_audit where action = 'PROFILE_TOLERANCE_CHANGE'",
				Integer.class))
			.isEqualTo(2);
	}

	@Test
	void theLimitsOfTheRangeAreAccepted() throws Exception {
		change(45).andExpect(status().isOk()).andExpect(jsonPath("$.tolerancePercent").value(45));
		change(0).andExpect(status().isOk()).andExpect(jsonPath("$.tolerancePercent").value(0));
	}

	@Test
	void aValueOutsideTheRangeIsRefusedAndNothingChanges() throws Exception {
		change(46).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tolerancePercent").value(containsString("mayor que 45")));
		change(-1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tolerancePercent").value(containsString("menor que 0")));
		perform(put(TOLERANCE).content("{}")).andExpect(status().isBadRequest());
		perform(put(TOLERANCE).content("""
				{"tolerancePercent": 12.5}
				""")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tolerancePercent").value(containsString("número entero")));

		perform(get(TOLERANCE)).andExpect(jsonPath("$.tolerancePercent").value(20))
			.andExpect(jsonPath("$.changes.length()").value(1));
	}

	@Test
	void theSameToleranceIsNotAChange() throws Exception {
		change(20).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.tolerancePercent").value(containsString("misma que la vigente")));
	}

	@Test
	void onlyAdministratorsSeeOrChangeIt() throws Exception {
		MockMvc kycLead = auth.mockMvcAs(context, auth.user(ConsoleRole.KYC_LEAD));

		kycLead.perform(get(TOLERANCE)).andExpect(status().isForbidden());
		kycLead.perform(put(TOLERANCE).contentType(MediaType.APPLICATION_JSON).content("""
				{"tolerancePercent": 30}
				""")).andExpect(status().isForbidden());
	}

	@Test
	void theValuesOfTheLogCannotBeChanged() throws Exception {
		change(30).andExpect(status().isOk());
		em.flush();

		assertThatThrownBy(() -> jdbc.update("update profile_tolerance set tolerance_percent = 40"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theValuesOfTheLogCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from profile_tolerance"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theDatabaseRefusesAToleranceOutsideTheRange() {
		assertThatThrownBy(() -> jdbc.update("insert into profile_tolerance (tolerance_percent) values (46)"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_profile_tolerance_range");
	}

	// ---------------------------------------------------------------- helpers

	private ResultActions change(int tolerancePercent) throws Exception {
		return perform(put(TOLERANCE).content("""
				{"tolerancePercent": %d}
				""".formatted(tolerancePercent)));
	}

	private String submittedRequest() throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()), "$.id");
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""")).andExpect(status().isOk());
		DuiStep.complete(admin, requestId, "01234567-8", "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content(PROFILE))
			.andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isOk());
		return requestId;
	}

	private String openAccount(String requestId) throws Exception {
		return read(perform(post("/api/console/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_%s", "lastFour": "4821"}
				""".formatted(requestId, requestId.substring(0, 8)))).andExpect(status().isCreated()), "$.id");
	}

	private ResultActions movement(String accountId, String externalId, String amount, String occurredAt)
			throws Exception {
		return perform(post("/api/console/accounts/{id}/movements", accountId).content("""
				{"externalId": "%s", "direction": "IN", "amountUsd": %s, "transactionTypeCode": "PAGO_SALARIO",
				 "occurredAt": "%s"}
				""".formatted(externalId, amount, occurredAt))).andExpect(status().isCreated());
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return admin.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
