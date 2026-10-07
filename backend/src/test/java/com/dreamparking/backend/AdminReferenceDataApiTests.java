package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/** Administrators maintain the catalogs, alert types, privacy notice and score rules through the API. */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class AdminReferenceDataApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	ConsoleUser admin;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		admin = auth.user(ConsoleRole.ADMIN);
		mvc = auth.mockMvcAs(context, admin);
	}

	@Test
	void onlyAdministratorsMaintainReferenceData() throws Exception {
		MockMvc analyst = auth.mockMvcAs(context, auth.user(ConsoleRole.FRAUD_ANALYST));
		analyst.perform(get("/api/console/admin/catalogs/income-sources")).andExpect(status().isForbidden());
		send(analyst, post("/api/console/admin/alert-types"), """
				{"code": "X", "description": "x", "defaultCriticality": "LOW"}
				""").andExpect(status().isForbidden());
		send(analyst, post("/api/console/admin/score-rules/R-01/versions"), """
				{"threshold": 1, "status": "CONFIRMED"}
				""").andExpect(status().isForbidden());
	}

	@Test
	void createsAndDeactivatesCatalogEntries() throws Exception {
		send(mvc, post("/api/console/admin/catalogs/income-sources"), """
				{"code": "HERENCIA", "label": "Herencia"}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.sortOrder").value(6));
		mvc.perform(get("/api/catalogs")).andExpect(jsonPath("$.incomeSources[*].code").value(hasItem("HERENCIA")));

		send(mvc, post("/api/console/admin/catalogs/income-sources"), """
				{"code": "HERENCIA", "label": "Otra"}
				""").andExpect(status().isConflict());
		send(mvc, post("/api/console/admin/catalogs/income-sources"), """
				{"code": "minusculas", "label": "x"}
				""").andExpect(status().isBadRequest());
		send(mvc, post("/api/console/admin/catalogs/income-sources"), """
				{"code": "CON_LIMITES", "label": "x", "maxUsd": 10}
				""").andExpect(status().isBadRequest());

		send(mvc, put("/api/console/admin/catalogs/transaction-types/AHORRO"), """
				{"label": "Ahorro", "sortOrder": 4, "active": false}
				""").andExpect(jsonPath("$.active").value(false));
		mvc.perform(get("/api/catalogs")).andExpect(jsonPath("$.transactionTypes[*].code").value(not(hasItem("AHORRO"))));
		mvc.perform(get("/api/console/admin/catalogs/transaction-types"))
			.andExpect(jsonPath("$[?(@.code == 'AHORRO')].active").value(hasItem(false)));

		mvc.perform(get("/api/console/admin/catalogs/no-existe")).andExpect(status().isNotFound());
	}

	@Test
	void rangesStayCoherentAndBoundsInUseAreFrozen() throws Exception {
		send(mvc, post("/api/console/admin/catalogs/income-ranges"), """
				{"code": "MAL", "label": "x", "minUsd": 900, "maxUsd": 100}
				""").andExpect(status().isBadRequest());
		send(mvc, post("/api/console/admin/catalogs/income-ranges"), """
				{"code": "SOLAPADO", "label": "USD 400 a 600", "minUsd": 400, "maxUsd": 600}
				""").andExpect(status().isConflict());

		// An inactive range can be prepared; activating it while it overlaps MAS_2500 is refused
		send(mvc, post("/api/console/admin/catalogs/income-ranges"), """
				{"code": "MAS_5000", "label": "Más de USD 5,000", "active": false, "minUsd": 5000.01}
				""").andExpect(status().isCreated());
		send(mvc, put("/api/console/admin/catalogs/income-ranges/MAS_5000"), """
				{"label": "Más de USD 5,000", "sortOrder": 5, "active": true, "minUsd": 5000.01}
				""").andExpect(status().isConflict());

		// HASTA_500 is in the demo KYC files: its label can be fixed, its bounds cannot change
		send(mvc, put("/api/console/admin/catalogs/income-ranges/HASTA_500"), """
				{"label": "Hasta USD 600", "sortOrder": 1, "active": true, "maxUsd": 600}
				""").andExpect(status().isConflict());
		send(mvc, put("/api/console/admin/catalogs/income-ranges/HASTA_500"), """
				{"label": "Hasta USD 500.00", "sortOrder": 1, "active": true, "maxUsd": 500}
				""").andExpect(status().isOk());
		mvc.perform(get("/api/catalogs"))
			.andExpect(jsonPath("$.incomeRanges[?(@.code == 'HASTA_500')].label").value(hasItem("Hasta USD 500.00")));
	}

	@Test
	void publishesANewPrivacyNoticeVersion() throws Exception {
		String hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
		send(mvc, post("/api/console/admin/privacy-notices"), "{\"version\": \"2026.2\", \"textHash\": \"" + hash + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.validTo").isEmpty());
		send(mvc, post("/api/console/admin/privacy-notices"), "{\"version\": \"2026.2\", \"textHash\": \"" + hash + "\"}")
			.andExpect(status().isConflict());
		mvc.perform(get("/api/console/admin/privacy-notices"))
			.andExpect(jsonPath("$[0].version").value("2026.2"))
			.andExpect(jsonPath("$[1].version").value("2026.1"))
			.andExpect(jsonPath("$[1].validTo").isNotEmpty());

		// New consents point to the version in force
		String requestId = read(mvc.perform(post("/api/onboarding/requests")), "$.id");
		send(mvc, put("/api/onboarding/requests/{id}/privacy-consent", requestId), "{\"signalsAccepted\": true}")
			.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("""
				select n.version from privacy_consent c join privacy_notice n on n.id = c.notice_id
				where c.request_id = ?::uuid""", String.class, requestId)).isEqualTo("2026.2");
	}

	@Test
	void maintainsAlertTypes() throws Exception {
		send(mvc, post("/api/console/admin/alert-types"), """
				{"code": "RETIROS_FRACCIONADOS", "description": "Retiros fraccionados", "defaultCriticality": "HIGH"}
				""").andExpect(status().isCreated());
		send(mvc, post("/api/console/admin/alert-types"), """
				{"code": "RETIROS_FRACCIONADOS", "description": "x", "defaultCriticality": "LOW"}
				""").andExpect(status().isConflict());
		send(mvc, put("/api/console/admin/alert-types/RETIROS_FRACCIONADOS"), """
				{"description": "Retiros fraccionados bajo el umbral", "defaultCriticality": "CRITICAL"}
				""").andExpect(jsonPath("$.defaultCriticality").value("CRITICAL"));
		mvc.perform(get("/api/console/alert-types"))
			.andExpect(jsonPath("$[?(@.code == 'RETIROS_FRACCIONADOS')].defaultCriticality").value(hasItem("CRITICAL")));
		send(mvc, put("/api/console/admin/alert-types/NO_EXISTE"), """
				{"description": "x", "defaultCriticality": "LOW"}
				""").andExpect(status().isNotFound());
	}

	@Test
	void aNewRuleVersionChangesTheThresholdForNewAssessmentsOnly() throws Exception {
		send(mvc, post("/api/console/admin/score-rules/R-01/versions"), """
				{"threshold": 150, "status": "DRAFT"}
				""").andExpect(jsonPath("$.version").value(2));
		mvc.perform(get("/api/console/score-rules")).andExpect(jsonPath("$[0].threshold").value(500));

		send(mvc, post("/api/console/admin/score-rules/R-01/versions"), """
				{"threshold": 200, "status": "CONFIRMED", "statusNote": "Umbral confirmado"}
				""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.version").value(3));
		mvc.perform(get("/api/console/score-rules"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].threshold").value(200));
		mvc.perform(get("/api/console/admin/score-rules/R-01/versions"))
			.andExpect(jsonPath("$[0].version").value(3))
			.andExpect(jsonPath("$[0].validTo").isEmpty())
			.andExpect(jsonPath("$[2].version").value(1))
			.andExpect(jsonPath("$[2].validTo").isNotEmpty());

		// 200_500 tops at 500 > 200 → no longer low; HASTA_200 tops at 200 → still low
		assertThat(score("200_500")).isEqualTo("PENDING_REVIEW");
		assertThat(score("HASTA_200")).isEqualTo("LOW");
		// The demo request scored with v1 keeps its assessment
		assertThat(jdbc.queryForObject("""
				select r.rule_version from risk_assessment a join score_rule r on r.id = a.rule_id
				where a.request_id = '00000000-0000-0000-0000-000000000418'::uuid and a.is_current""", Integer.class))
			.isEqualTo(1);

		send(mvc, post("/api/console/admin/score-rules/R-01/versions"), """
				{"threshold": 300, "status": "RETIRED"}
				""").andExpect(status().isBadRequest());
		mvc.perform(get("/api/console/admin/score-rules/R-99/versions")).andExpect(status().isNotFound());
		mvc.perform(get("/api/console/users/{id}/access-audit", admin.getId()))
			.andExpect(jsonPath("$[*].action").value(hasItem("SCORE_RULE_PUBLISH")));
	}

	private String score(String monthlyAmountRangeCode) throws Exception {
		String requestId = read(mvc.perform(post("/api/onboarding/requests")), "$.id");
		return read(send(mvc, put("/api/onboarding/requests/{id}/expected-activity", requestId),
				"{\"transactionTypeCode\": \"PAGO_SALARIO\", \"monthlyAmountRangeCode\": \"" + monthlyAmountRangeCode + "\"}"),
				"$.level");
	}

	private static ResultActions send(MockMvc mvc, MockHttpServletRequestBuilder request, String body) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
