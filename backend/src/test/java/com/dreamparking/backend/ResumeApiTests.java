package com.dreamparking.backend;

import static org.hamcrest.Matchers.hasItem;
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
 * VDI-86: a request saved half-way is resumed from its last step. Each step is saved when it is completed, so resuming
 * reports where the request stopped (with the DUI step skipped while it is optional) and hands back what was saved.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ResumeApiTests {

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
	void aNewRequestResumesFromTheFirstStep() throws Exception {
		String id = startRequest();

		mvc.perform(get("/api/onboarding/requests/{id}/resume", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requestId").value(id))
			.andExpect(jsonPath("$.state").value("RESUMABLE"))
			.andExpect(jsonPath("$.currentStep").value("PRIVACY_NOTICE"))
			.andExpect(jsonPath("$.completedSteps").value(0))
			.andExpect(jsonPath("$.firstName").isEmpty())
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andExpect(jsonPath("$.steps.length()").value(6))
			.andExpect(jsonPath("$.steps[0].state").value("CURRENT"))
			.andExpect(jsonPath("$.steps[1].state").value("PENDING"))
			.andExpect(jsonPath("$.saved.basicData").isEmpty())
			.andExpect(jsonPath("$.saved.income").isEmpty());
	}

	@Test
	void afterTheBasicDataItContinuesAtTheIncomeAndSkipsTheOptionalDui() throws Exception {
		String id = startRequest();
		saveConsentAndBasicData(id);

		mvc.perform(get("/api/onboarding/requests/{id}/resume", id))
			.andExpect(jsonPath("$.state").value("RESUMABLE"))
			.andExpect(jsonPath("$.currentStep").value("INCOME"))
			.andExpect(jsonPath("$.completedSteps").value(2))
			.andExpect(jsonPath("$.firstName").value("Ana"))
			.andExpect(jsonPath("$.steps[0].step").value("PRIVACY_NOTICE"))
			.andExpect(jsonPath("$.steps[0].state").value("DONE"))
			.andExpect(jsonPath("$.steps[0].savedAt").isNotEmpty())
			.andExpect(jsonPath("$.steps[1].state").value("DONE"))
			.andExpect(jsonPath("$.steps[1].savedAt").isNotEmpty())
			.andExpect(jsonPath("$.steps[2].step").value("IDENTITY_DOCUMENT"))
			.andExpect(jsonPath("$.steps[2].state").value("SKIPPED"))
			.andExpect(jsonPath("$.steps[2].savedAt").isEmpty())
			.andExpect(jsonPath("$.steps[3].state").value("CURRENT"))
			.andExpect(jsonPath("$.steps[4].state").value("PENDING"))
			.andExpect(jsonPath("$.saved.basicData.dui").value("01234567-8"))
			.andExpect(jsonPath("$.saved.basicData.firstNames").value("Ana Sofía"))
			.andExpect(jsonPath("$.saved.basicData.lastNames").value("Pérez López"))
			.andExpect(jsonPath("$.saved.basicData.mobilePhone").value("7123-4567"))
			.andExpect(jsonPath("$.saved.identityDocument").isEmpty())
			.andExpect(jsonPath("$.saved.income").isEmpty());
	}

	@Test
	void afterTheMoneyStepItContinuesAtTheReviewWithEverythingSaved() throws Exception {
		String id = startRequest();
		saveConsentAndBasicData(id);
		send(id, "income", "{\"sourceCode\": \"SALARIO\", \"rangeCode\": \"HASTA_500\"}").andExpect(status().isNoContent());
		send(id, "expected-activity", "{\"transactionTypeCode\": \"AHORRO\", \"monthlyAmountRangeCode\": \"200_500\"}")
			.andExpect(status().isOk());
		send(id, "expected-activity", "{\"transactionTypeCode\": \"AHORRO\", \"monthlyAmountRangeCode\": \"500_1000\"}")
			.andExpect(status().isOk());

		mvc.perform(get("/api/onboarding/requests/{id}/resume", id))
			.andExpect(jsonPath("$.currentStep").value("REVIEW"))
			.andExpect(jsonPath("$.steps[3].state").value("DONE"))
			.andExpect(jsonPath("$.steps[3].savedAt").isNotEmpty())
			.andExpect(jsonPath("$.steps[4].state").value("DONE"))
			.andExpect(jsonPath("$.steps[5].state").value("CURRENT"))
			.andExpect(jsonPath("$.saved.income.sourceCode").value("SALARIO"))
			.andExpect(jsonPath("$.saved.income.rangeCode").value("HASTA_500"))
			.andExpect(jsonPath("$.saved.expectedActivity.transactionTypeCode").value("AHORRO"))
			.andExpect(jsonPath("$.saved.expectedActivity.monthlyAmountRangeCode").value("500_1000"))
			.andExpect(jsonPath("$.saved.expectedActivity.version").value(2));
	}

	@Test
	void aSubmittedRequestCannotBeResumedAndShowsNoSavedData() throws Exception {
		String id = startRequest();
		saveConsentAndBasicData(id);
		send(id, "income", "{\"sourceCode\": \"SALARIO\", \"rangeCode\": \"HASTA_500\"}").andExpect(status().isNoContent());
		send(id, "expected-activity", "{\"transactionTypeCode\": \"AHORRO\", \"monthlyAmountRangeCode\": \"200_500\"}")
			.andExpect(status().isOk());
		mvc.perform(post("/api/onboarding/requests/{id}/submit", id)).andExpect(status().isOk());

		mvc.perform(get("/api/onboarding/requests/{id}/resume", id))
			.andExpect(jsonPath("$.state").value("COMPLETED"))
			.andExpect(jsonPath("$.number").isNotEmpty())
			.andExpect(jsonPath("$.currentStep").isEmpty())
			.andExpect(jsonPath("$.expiresAt").isEmpty())
			.andExpect(jsonPath("$.saved").isEmpty())
			.andExpect(jsonPath("$.steps[5].state").value("DONE"))
			.andExpect(jsonPath("$.steps[5].savedAt").isNotEmpty());
		mvc.perform(post("/api/onboarding/requests/{id}/abandon", id)).andExpect(status().isConflict());
	}

	@Test
	void startingOverAbandonsTheRequestAndItCanNoLongerBeResumedOrChanged() throws Exception {
		String id = startRequest();
		saveConsentAndBasicData(id);

		mvc.perform(post("/api/onboarding/requests/{id}/abandon", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ABANDONED"))
			.andExpect(jsonPath("$.completedSteps").value(2));

		mvc.perform(get("/api/onboarding/requests/{id}/resume", id))
			.andExpect(jsonPath("$.state").value("ABANDONED"))
			.andExpect(jsonPath("$.currentStep").isEmpty())
			.andExpect(jsonPath("$.saved").isEmpty())
			.andExpect(jsonPath("$.steps[1].state").value("DONE"))
			.andExpect(jsonPath("$.steps[3].state").value("PENDING"));
		send(id, "income", "{\"sourceCode\": \"SALARIO\", \"rangeCode\": \"HASTA_500\"}").andExpect(status().isConflict());
		mvc.perform(post("/api/onboarding/requests/{id}/abandon", id)).andExpect(status().isConflict());
		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("REQUEST_ABANDONED")));
	}

	@Test
	void aRequestIdleForMoreThanTheWindowIsExpiredButStillAbandonable() throws Exception {
		String id = startRequest();
		saveConsentAndBasicData(id);
		em.flush();
		em.clear();
		jdbc.update("update onboarding_request set last_activity_at = now() - interval '8 days' where id = ?::uuid", id);

		mvc.perform(get("/api/onboarding/requests/{id}/resume", id))
			.andExpect(jsonPath("$.state").value("EXPIRED"))
			.andExpect(jsonPath("$.currentStep").isEmpty())
			.andExpect(jsonPath("$.saved").isEmpty())
			.andExpect(jsonPath("$.expiresAt").isNotEmpty())
			.andExpect(jsonPath("$.completedSteps").value(2));
		mvc.perform(post("/api/onboarding/requests/{id}/abandon", id)).andExpect(status().isOk());

		String recent = startRequest();
		em.flush();
		em.clear();
		jdbc.update("update onboarding_request set last_activity_at = now() - interval '6 days' where id = ?::uuid",
				recent);
		mvc.perform(get("/api/onboarding/requests/{id}/resume", recent)).andExpect(jsonPath("$.state").value("RESUMABLE"));
	}

	@Test
	void anUnknownRequestIsNotFound() throws Exception {
		mvc.perform(get("/api/onboarding/requests/{id}/resume", UUID.randomUUID())).andExpect(status().isNotFound());
		mvc.perform(post("/api/onboarding/requests/{id}/abandon", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	private void saveConsentAndBasicData(String id) throws Exception {
		send(id, "privacy-consent", "{\"signalsAccepted\": true}").andExpect(status().isOk());
		send(id, "basic-data", """
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""").andExpect(status().isOk());
	}

	private ResultActions send(String id, String resource, String body) throws Exception {
		return mvc.perform(put("/api/onboarding/requests/{id}/{resource}", id, resource)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private String startRequest() throws Exception {
		return JsonPath.read(mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString(),
				"$.id");
	}

}
