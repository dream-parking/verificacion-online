package com.dreamparking.backend;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * Mobile app flow from start to submit, then the console: request detail, account opening and the alert workflow,
 * each action done as the console user of the session.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class FullFlowApiTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	ConsoleUser admin;

	ConsoleUser ana;

	ConsoleUser luis;

	ConsoleUser lead;

	@BeforeEach
	void users() {
		admin = auth.user(ConsoleRole.ADMIN);
		ana = auth.user(ConsoleRole.FRAUD_ANALYST);
		luis = auth.user(ConsoleRole.FRAUD_ANALYST);
		lead = auth.user(ConsoleRole.KYC_LEAD);
	}

	@Test
	void customerCompletesOnboardingAndConsoleWorksTheAlert() throws Exception {
		MockMvc app = auth.mockMvcAs(context, admin);
		String requestId = read(perform(app, post("/api/onboarding/requests")).andExpect(status().isCreated()), "$.id");

		// ---- mobile app
		perform(app, put("/api/onboarding/requests/{id}/signals", requestId).content("""
				{"deviceFingerprint": "aa11·bb22·cc33", "deviceModel": "Galaxy A54", "operatingSystem": "Android",
				 "countryIso": "SV", "typingSpeedCpm": 190}
				"""))
			.andExpect(status().isNoContent());
		perform(app, put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				"""))
			.andExpect(jsonPath("$.completedSteps").value(1));
		perform(app, put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				"""))
			.andExpect(jsonPath("$.completedSteps").value(2));
		perform(app, put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				"""))
			.andExpect(status().isNoContent());
		perform(app, put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500"}
				"""))
			.andExpect(jsonPath("$.level").value("LOW"));
		perform(app, post("/api/onboarding/requests/{id}/submit", requestId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.completedSteps").value(5))
			.andExpect(jsonPath("$.number").value(matchesPattern("SOL-\\d{4}-\\d{5}")));

		// ---- console: the detail has the whole file
		perform(app, get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.applicant.dui").value("01234567-8"))
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("REQUEST_SUBMITTED")));

		// ---- account opened in the core for the submitted request
		String accountId = read(perform(app, post("/api/console/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_test_1234", "lastFour": "1234"}
				""".formatted(requestId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.maskedNumber").value("•••• 1234")), "$.id");
		perform(app, post("/api/console/accounts").content("""
				{"requestId": "%s", "numberToken": "tok_test_9999", "lastFour": "9999"}
				""".formatted(requestId)))
			.andExpect(status().isConflict());

		// ---- alert workflow
		String alertId = read(perform(app, post("/api/console/alerts").content("""
				{"accountId": "%s", "typeCode": "PERFIL_EXCEDIDO", "reason": "Movimientos 4 veces por encima del perfil",
				 "evidence": {"factor": 4}}
				""".formatted(accountId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.criticality").value("CRITICAL"))
			.andExpect(jsonPath("$.status").value("UNASSIGNED")), "$.id");

		perform(as(ana), post("/api/console/alerts/{id}/take", alertId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ASSIGNED"))
			.andExpect(jsonPath("$.assigneeId").value(ana.getId().toString()));
		perform(as(luis), post("/api/console/alerts/{id}/take", alertId)).andExpect(status().isConflict());
		perform(as(luis), post("/api/console/alerts/{id}/review", alertId)).andExpect(status().isForbidden());
		perform(as(ana), post("/api/console/alerts/{id}/review", alertId).content("""
				{"comment": "Revisando estados de cuenta"}
				"""))
			.andExpect(jsonPath("$.status").value("IN_REVIEW"));
		perform(as(luis), post("/api/console/alerts/{id}/close", alertId).content("""
				{"resolution": "FALSO_POSITIVO"}
				"""))
			.andExpect(status().isForbidden());
		perform(as(lead), post("/api/console/alerts/{id}/close", alertId).content("""
				{"resolution": "FALSO_POSITIVO", "comment": "Pago de aguinaldo"}
				"""))
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.resolution").value("FALSO_POSITIVO"));

		perform(app, get("/api/console/alerts/{id}/history", alertId)).andExpect(jsonPath("$.length()").value(4));
		perform(app, get("/api/console/alerts")).andExpect(jsonPath("$[*].id").value(not(hasItem(alertId))));
		perform(app, get("/api/console/users/{id}/access-audit", ana.getId()))
			.andExpect(jsonPath("$[0].action").value("TAKE_ALERT"));
		perform(as(ana), get("/api/console/users/{id}/access-audit", ana.getId())).andExpect(status().isForbidden());
	}

	@Test
	void requestCannotBeSubmittedBeforeAllSteps() throws Exception {
		MockMvc app = auth.mockMvcAs(context, admin);
		String requestId = read(perform(app, post("/api/onboarding/requests")), "$.id");
		perform(app, post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isConflict());
	}

	@Test
	void privacyNoticeMustBeAccepted() throws Exception {
		MockMvc app = auth.mockMvcAs(context, admin);
		String requestId = read(perform(app, post("/api/onboarding/requests")), "$.id");
		perform(app, put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": false}
				"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void consoleDataNeedsASession() throws Exception {
		MockMvc anonymous = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
			.apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
			.build();
		anonymous.perform(get("/api/console/customers").param("dui", "04812377-5")).andExpect(status().isUnauthorized());
		anonymous.perform(get("/api/console/accounts/{id}", java.util.UUID.randomUUID()))
			.andExpect(status().isUnauthorized());
	}

	private MockMvc as(ConsoleUser user) {
		return auth.mockMvcAs(context, user);
	}

	private static ResultActions perform(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
