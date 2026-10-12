package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-90: every change of the declared transactional profile (type of money and monthly amount range) is a new
 * version; the latest is the one in force, the earlier ones are kept as they were declared and cannot be changed.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ExpectedActivityVersionApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void theFirstDeclarationIsVersion1AndEachChangeIsANewVersion() throws Exception {
		String id = startRequest();

		declare(id, "PAGO_SALARIO", "HASTA_200").andExpect(status().isOk());
		assertThat(versions(id)).containsExactly("1:PAGO_SALARIO:HASTA_200");

		declare(id, "PAGO_SALARIO", "500_1000").andExpect(status().isOk());
		declare(id, "AHORRO", "500_1000").andExpect(status().isOk());

		assertThat(versions(id)).containsExactly("1:PAGO_SALARIO:HASTA_200", "2:PAGO_SALARIO:500_1000",
				"3:AHORRO:500_1000");
		sync();
		assertThat(jdbc.queryForObject("select version from expected_activity where request_id = ?::uuid",
				Integer.class, id)).isEqualTo(3);
	}

	@Test
	void declaringTheSameProfileAgainDoesNotCreateAVersion() throws Exception {
		String id = startRequest();
		declare(id, "PAGO_SALARIO", "200_500").andExpect(status().isOk());

		declare(id, "PAGO_SALARIO", "200_500").andExpect(status().isOk()).andExpect(jsonPath("$.score").value(4));

		assertThat(versions(id)).containsExactly("1:PAGO_SALARIO:200_500");
		sync();
		assertThat(jdbc.queryForObject(
				"select count(*) from request_event where request_id = ?::uuid and type = 'EXPECTED_ACTIVITY_REGISTERED'",
				Integer.class, id)).isEqualTo(1);
	}

	@Test
	void aRejectedDeclarationLeavesNoVersion() throws Exception {
		String id = startRequest();
		declare(id, "PAGO_SALARIO", "200_500").andExpect(status().isOk());

		declare(id, "PAGO_SALARIO", "NO_EXISTE").andExpect(status().isBadRequest());

		assertThat(versions(id)).containsExactly("1:PAGO_SALARIO:200_500");
	}

	@Test
	void theConsoleSeesTheVersionInForceFirstAndTheChangesInTheTimeline() throws Exception {
		String id = startRequest();
		declare(id, "PAGO_SALARIO", "HASTA_200").andExpect(status().isOk());
		declare(id, "AHORRO", "500_1000").andExpect(status().isOk());

		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expectedActivity.version").value(2))
			.andExpect(jsonPath("$.expectedActivity.monthlyAmountRangeCode").value("500_1000"))
			.andExpect(jsonPath("$.expectedActivityVersions.length()").value(2))
			.andExpect(jsonPath("$.expectedActivityVersions[0].version").value(2))
			.andExpect(jsonPath("$.expectedActivityVersions[0].current").value(true))
			.andExpect(jsonPath("$.expectedActivityVersions[0].transactionTypeCode").value("AHORRO"))
			.andExpect(jsonPath("$.expectedActivityVersions[0].monthlyAmountRangeLabel").value("USD 500.01 a 1,000"))
			.andExpect(jsonPath("$.expectedActivityVersions[0].registeredAt").isNotEmpty())
			.andExpect(jsonPath("$.expectedActivityVersions[1].version").value(1))
			.andExpect(jsonPath("$.expectedActivityVersions[1].current").value(false))
			.andExpect(jsonPath("$.expectedActivityVersions[1].monthlyAmountRangeCode").value("HASTA_200"))
			.andExpect(jsonPath("$.timeline[*].description")
				.value(hasItem("Perfil transaccional cambiado: versión 2")));
	}

	@Test
	void aRequestWithoutProfileHasNoVersions() throws Exception {
		String id = startRequest();

		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.expectedActivity").isEmpty())
			.andExpect(jsonPath("$.expectedActivityVersions.length()").value(0));
	}

	@Test
	void theProfileOfARequestThatIsNotInProgressCannotBeDeclared() throws Exception {
		String id = startRequest();
		jdbc.update("update onboarding_request set status = 'ABANDONED' where id = ?::uuid", id);
		sync();

		declare(id, "PAGO_SALARIO", "HASTA_200").andExpect(status().isConflict());

		assertThat(versions(id)).isEmpty();
	}

	@Test
	void versionsAreCopiedFromTheDeclarationsAlreadyOnFile() {
		assertThat(jdbc.queryForObject("""
				select count(*) from expected_activity ea
				join expected_activity_version v on v.request_id = ea.request_id and v.version = ea.version
				 and v.transaction_type_code = ea.transaction_type_code
				 and v.monthly_amount_range_code = ea.monthly_amount_range_code""", Integer.class))
			.isEqualTo(jdbc.queryForObject("select count(*) from expected_activity", Integer.class))
			.isPositive();
	}

	@Test
	void aVersionCannotBeEditedEvenInARequestInProgress() throws Exception {
		String id = startRequest();
		declare(id, "PAGO_SALARIO", "HASTA_200").andExpect(status().isOk());
		sync();

		assertThatThrownBy(() -> jdbc.update(
				"update expected_activity_version set monthly_amount_range_code = '200_500' where request_id = ?::uuid",
				id)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("cannot be modified");
	}

	@Test
	void aVersionCannotBeDeletedEvenInARequestInProgress() throws Exception {
		String id = startRequest();
		declare(id, "PAGO_SALARIO", "HASTA_200").andExpect(status().isOk());
		sync();

		assertThatThrownBy(
				() -> jdbc.update("delete from expected_activity_version where request_id = ?::uuid", id))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void deletingAnAbandonedRequestStillDeletesItsVersions() throws Exception {
		String id = startRequest();
		declare(id, "PAGO_SALARIO", "HASTA_200").andExpect(status().isOk());
		declare(id, "AHORRO", "HASTA_200").andExpect(status().isOk());
		sync();
		jdbc.update("update onboarding_request set status = 'ABANDONED' where id = ?::uuid", id);

		jdbc.update("delete from onboarding_request where id = ?::uuid", id);

		assertThat(versions(id)).isEmpty();
	}

	/** What the API wrote has to reach the database before it is read or changed with SQL. */
	private void sync() {
		em.flush();
		em.clear();
	}

	/** {@code version:type:range} of each version, oldest first. */
	private List<String> versions(String id) {
		sync();
		return jdbc.queryForList("""
				select version || ':' || transaction_type_code || ':' || monthly_amount_range_code
				from expected_activity_version where request_id = ?::uuid order by version""", String.class, id);
	}

	private ResultActions declare(String id, String type, String range) throws Exception {
		return mvc.perform(put("/api/onboarding/requests/{id}/expected-activity", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"transactionTypeCode\": \"" + type + "\", \"monthlyAmountRangeCode\": \"" + range + "\"}"));
	}

	private String startRequest() throws Exception {
		return JsonPath.read(mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString(),
				"$.id");
	}

}
