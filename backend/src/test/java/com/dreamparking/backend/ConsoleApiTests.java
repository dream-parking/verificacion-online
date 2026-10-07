package com.dreamparking.backend;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/** Console read side (request list and detail, rule view) and the analyst inbox (VDI-56, VDI-57, VDI-61, VDI-62, VDI-63). */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ConsoleApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	MockMvc mvc;

	@BeforeEach
	void signIn() {
		mvc = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	// ---- Requests

	@Test
	void listsRequestsAndFiltersThem() throws Exception {
		mvc.perform(get("/api/console/requests").param("q", "rivas"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].number").value("SOL-2026-00418"))
			.andExpect(jsonPath("$.content[0].name").value("Marta Alejandra Rivas Cruz"))
			.andExpect(jsonPath("$.content[0].riskLevel").value("LOW"))
			.andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
			.andExpect(jsonPath("$.content[0].monthlyAmountRangeLabel").value("USD 200.01 a 500"));

		mvc.perform(get("/api/console/requests").param("q", "sol-2026-00418").param("riskLevel", "LOW"))
			.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/console/requests").param("q", "rivas").param("status", "IN_PROGRESS"))
			.andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/console/requests").param("q", "100%"))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void paginatesAndRejectsBadFilters() throws Exception {
		mvc.perform(get("/api/console/requests").param("size", "1").param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.page").value(0));

		mvc.perform(get("/api/console/requests").param("status", "NO_EXISTE")).andExpect(status().isBadRequest());
	}

	@Test
	void showsTheRequestDetail() throws Exception {
		mvc.perform(get("/api/console/requests/{id}", EntityMappingTests.DEMO_REQUEST))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.number").value("SOL-2026-00418"))
			.andExpect(jsonPath("$.applicant.dui").value("04812377-5"))
			.andExpect(jsonPath("$.income.sourceCode").value("SALARIO"))
			.andExpect(jsonPath("$.income.rangeLabel").value("USD 500.01 a 1,000"))
			.andExpect(jsonPath("$.income.registeredAt").isNotEmpty())
			.andExpect(jsonPath("$.expectedActivity.monthlyAmountRangeCode").value("200_500"))
			.andExpect(jsonPath("$.expectedActivity.monthlyAmountRangeLabel").value("USD 200.01 a 500"))
			.andExpect(jsonPath("$.expectedActivity.registeredAt").isNotEmpty())
			.andExpect(jsonPath("$.risk.level").value("LOW"))
			.andExpect(jsonPath("$.risk.ruleCode").value("R-01"))
			.andExpect(jsonPath("$.risk.ruleThreshold").value(500))
			.andExpect(jsonPath("$.signals.ip").value("190.5.142.77"))
			.andExpect(jsonPath("$.signals.typingPace").value("NORMAL"))
			.andExpect(jsonPath("$.steps", hasSize(5)))
			.andExpect(jsonPath("$.timeline").isNotEmpty());
	}

	@Test
	void detailOfAnInProgressRequestHasNoLaterSections() throws Exception {
		String body = mvc.perform(post("/api/onboarding/requests")).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/console/requests/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.number").isEmpty())
			.andExpect(jsonPath("$.income").isEmpty())
			.andExpect(jsonPath("$.expectedActivity").isEmpty())
			.andExpect(jsonPath("$.risk").isEmpty())
			.andExpect(jsonPath("$.signals").isEmpty())
			.andExpect(jsonPath("$.steps", hasSize(0)));
	}

	@Test
	void unknownRequestIsNotFound() throws Exception {
		mvc.perform(get("/api/console/requests/{id}", UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").isNotEmpty());
	}

	// ---- Rules

	@Test
	void showsTheRuleInForce() throws Exception {
		mvc.perform(get("/api/console/score-rules"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].code").value("R-01"))
			.andExpect(jsonPath("$[0].threshold").value(500))
			.andExpect(jsonPath("$[0].operator").value("<="))
			.andExpect(jsonPath("$[0].resultIfMatched").value("LOW"))
			.andExpect(jsonPath("$[0].status").value("PROVISIONAL"));
	}

	// ---- Alerts

	@Test
	void listsAlertsMostCriticalFirst() throws Exception {
		mvc.perform(get("/api/console/alerts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(9)))
			.andExpect(jsonPath("$[0].criticality").value("CRITICAL"))
			.andExpect(jsonPath("$[0].account").value("•••• 4821"))
			.andExpect(jsonPath("$[1].account").value("•••• 7730"))
			.andExpect(jsonPath("$[8].criticality").value("LOW"))
			.andExpect(jsonPath("$[2].criticality").value("HIGH"));
	}

	@Test
	void filtersAlerts() throws Exception {
		mvc.perform(get("/api/console/alerts").param("status", "UNASSIGNED"))
			.andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[?(@.assigneeId != null)]").isEmpty());
		mvc.perform(get("/api/console/alerts").param("status", "ASSIGNED"))
			.andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[*].status", everyItem(is("ASSIGNED"))));
		mvc.perform(get("/api/console/alerts").param("account", "7730"))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].assigneeName").value("Ana Beltrán"));
		mvc.perform(get("/api/console/alerts").param("from", "2026-10-05").param("to", "2026-10-05"))
			.andExpect(jsonPath("$", hasSize(3)));
		mvc.perform(get("/api/console/alerts").param("from", "2026-10-06")).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/console/alerts").param("from", "2026-10-05").param("to", "2026-10-01"))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/console/alerts").param("from", "05/10/2026")).andExpect(status().isBadRequest());
	}

	@Test
	void takesAnUnassignedAlertOnlyOnce() throws Exception {
		var ana = auth.user(ConsoleRole.FRAUD_ANALYST);
		var luis = auth.user(ConsoleRole.FRAUD_ANALYST);
		String alertId = unassignedAlertId();

		auth.mockMvcAs(context, ana).perform(post("/api/console/alerts/{id}/take", alertId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ASSIGNED"))
			.andExpect(jsonPath("$.assigneeId").value(ana.getId().toString()))
			.andExpect(jsonPath("$.assigneeName").value("Usuario de Prueba"));

		auth.mockMvcAs(context, luis).perform(post("/api/console/alerts/{id}/take", alertId))
			.andExpect(status().isConflict());
	}

	@Test
	void takingAnUnknownAlertIsNotFound() throws Exception {
		mvc.perform(post("/api/console/alerts/{id}/take", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	// ---- Users

	@Test
	void listsActiveConsoleUsers() throws Exception {
		mvc.perform(get("/api/console/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(5))) // the 4 demo users and the administrator signed in for this test
			.andExpect(jsonPath("$[?(@.email=='grosales@ceiba.example')].role").value("KYC_LEAD"));
	}

	private String unassignedAlertId() throws Exception {
		return JsonPath.read(mvc.perform(get("/api/console/alerts").param("account", "1156"))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$[0].id");
	}

}
