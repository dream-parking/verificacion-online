package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-143: an analyst escalates a request held for enhanced review to a video call or to a branch. The request keeps
 * what it captured, moves to ESCALATED with the channel, and the escalation is a decision in the audit log with the
 * channel, the reason, who and when. Any other status is refused. The app sees the status and the channel, not the
 * score. What the console shows comes in Spanish.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EscalationApiTests {

	private static final String ESCALATION = "/api/console/requests/{id}/escalation";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	ConsoleUser analyst;

	MockMvc console;

	@BeforeEach
	void signIn() {
		analyst = auth.user(ConsoleRole.KYC_LEAD);
		console = auth.mockMvcAs(context, analyst);
	}

	@Test
	void aRequestHeldForEnhancedReviewIsEscalatedWithItsChannelAndKeepsItsProgress() throws Exception {
		String requestId = held();

		perform(post(ESCALATION, requestId).content("""
				{"channel": "VIDEO_CALL", "reason": "El monto declarado no coincide con los ingresos"}
				""")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.decision").value("ESCALATED"))
			.andExpect(jsonPath("$.channel").value("VIDEO_CALL"))
			.andExpect(jsonPath("$.decidedBy").value(analyst.getFullName()))
			.andExpect(jsonPath("$.decidedAt").isNotEmpty())
			.andExpect(jsonPath("$.reason")
				.value("Escalada a videollamada por Usuario de Prueba: El monto declarado no coincide con los ingresos"));
		em.flush();
		em.clear();

		perform(get("/api/console/requests/{id}", requestId)).andExpect(jsonPath("$.status").value("ESCALATED"))
			.andExpect(jsonPath("$.completedSteps").value(6))
			.andExpect(jsonPath("$.number").isNotEmpty())
			.andExpect(jsonPath("$.submittedAt").isNotEmpty())
			.andExpect(jsonPath("$.riskDecisions.length()").value(2))
			.andExpect(jsonPath("$.riskDecisions[0].decision").value("ENHANCED_REVIEW"))
			.andExpect(jsonPath("$.riskDecisions[0].channel").doesNotExist())
			.andExpect(jsonPath("$.riskDecisions[1].decision").value("ESCALATED"))
			.andExpect(jsonPath("$.riskDecisions[1].channel").value("VIDEO_CALL"))
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("REQUEST_ESCALATED")))
			.andExpect(jsonPath("$.timeline[?(@.type == 'REQUEST_ESCALATED')].description")
				.value(hasItem("Solicitud escalada a videollamada: El monto declarado no coincide con los ingresos")));
		assertThat(jdbc.queryForObject("select decided_by_user from risk_decision_log where decision = 'ESCALATED'"
				+ " and request_id = ?::uuid", String.class, requestId))
			.isEqualTo(analyst.getId().toString());
	}

	@Test
	void theAppSeesTheStatusAndTheChannelButNotTheScore() throws Exception {
		String requestId = held();
		escalate(requestId, "BRANCH").andExpect(status().isCreated());
		em.flush();
		em.clear();

		perform(get("/api/onboarding/requests/{id}", requestId)).andExpect(jsonPath("$.status").value("ESCALATED"))
			.andExpect(jsonPath("$.escalationChannel").value("BRANCH"))
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.reason").doesNotExist());
		perform(get("/api/onboarding/requests/{id}/resume", requestId)).andExpect(jsonPath("$.state").value("COMPLETED"));
	}

	@Test
	void aRequestThatIsNotHeldCannotBeEscalated() throws Exception {
		String approved = submitted("200_500");

		escalate(approved, "VIDEO_CALL").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail")
				.value("Solo se puede escalar una solicitud en revisión reforzada; esta está completada"));
		perform(get("/api/onboarding/requests/{id}", approved)).andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.escalationChannel").doesNotExist());
	}

	@Test
	void anEscalatedRequestCannotBeEscalatedAgain() throws Exception {
		String requestId = held();
		escalate(requestId, "VIDEO_CALL").andExpect(status().isCreated());

		escalate(requestId, "BRANCH").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("esta está escalada")));
		em.flush();
		assertThat(escalations(requestId)).isEqualTo(1);
	}

	@Test
	void anUnknownRequestIsNotFound() throws Exception {
		escalate("00000000-0000-0000-0000-000000000000", "VIDEO_CALL").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("No existe la solicitud 00000000-0000-0000-0000-000000000000"));
	}

	@Test
	void theChannelAndAWellFormedReasonAreRequired() throws Exception {
		String requestId = held();

		perform(post(ESCALATION, requestId).content("""
				{"reason": "Monto alto"}
				""")).andExpect(status().isBadRequest());
		perform(post(ESCALATION, requestId).content("""
				{"channel": "TELEFONO", "reason": "Monto alto"}
				""")).andExpect(status().isBadRequest());
		perform(post(ESCALATION, requestId).content("""
				{"channel": "BRANCH", "reason": "   "}
				""")).andExpect(status().isBadRequest());
		perform(post(ESCALATION, requestId).content("""
				{"channel": "BRANCH", "reason": "<script>"}
				""")).andExpect(status().isBadRequest());
		perform(post(ESCALATION, requestId).content("""
				{"channel": "BRANCH", "reason": "%s"}
				""".formatted("a".repeat(251)))).andExpect(status().isBadRequest());
		em.flush();
		assertThat(escalations(requestId)).isZero();
	}

	@Test
	void everyConsoleRoleCanEscalateButNotWithoutASession() throws Exception {
		MockMvc fraud = auth.mockMvcAs(context, auth.user(ConsoleRole.FRAUD_ANALYST));
		MockMvc admin = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
		MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context)
			.apply(SecurityMockMvcConfigurers.springSecurity())
			.build();
		String body = """
				{"channel": "BRANCH", "reason": "Revisión presencial"}
				""";

		anonymous.perform(post(ESCALATION, held()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isUnauthorized());
		fraud.perform(post(ESCALATION, held()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
		admin.perform(post(ESCALATION, held()).contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
	}

	@Test
	void eachEscalationIsRecordedInTheAccessAudit() throws Exception {
		String requestId = held();
		escalate(requestId, "VIDEO_CALL").andExpect(status().isCreated());
		em.flush();

		assertThat(jdbc.queryForObject("""
				select count(*) from access_audit
				where action = 'ESCALATE_REQUEST' and entity_name = 'onboarding_request' and entity_id = ? and user_id = ?""",
				Integer.class, requestId, analyst.getId())).isEqualTo(1);
	}

	@Test
	void theDatabaseRefusesAnEscalatedRequestWithoutChannel() throws Exception {
		String requestId = held();

		assertThatThrownBy(() -> jdbc.update("update onboarding_request set status = 'ESCALATED' where id = ?::uuid",
				requestId))
			.hasMessageContaining("ck_request_escalation_channel");
	}

	// ---------------------------------------------------------------- helpers

	private int escalations(String requestId) {
		return jdbc.queryForObject(
				"select count(*) from risk_decision_log where decision = 'ESCALATED' and request_id = ?::uuid",
				Integer.class, requestId);
	}

	private ResultActions escalate(String requestId, String channel) throws Exception {
		return perform(post(ESCALATION, requestId).content("""
				{"channel": "%s", "reason": "Monto alto para los ingresos"}
				""".formatted(channel)));
	}

	/** A submitted request held for enhanced review: a model in force with a threshold of 2 and a high amount. */
	private String held() throws Exception {
		if (jdbc.queryForObject("select count(*) from score_model where version = 99", Integer.class) == 0) {
			jdbc.update("update score_model set valid_to = now() where valid_to is null");
			jdbc.update("""
					insert into score_model (version, name, low_max, medium_max, valid_from)
					values (99, 'Strict test model', 1, 2, now())""");
			jdbc.update("insert into score_model_weight (model_version, signal, max_points) values (99, 'AMOUNT', 25)");
		}
		String requestId = submitted("MAS_1000");
		em.flush();
		assertThat(jdbc.queryForObject("select status::text from onboarding_request where id = ?::uuid", String.class,
				requestId))
			.isEqualTo("ENHANCED_REVIEW");
		return requestId;
	}

	/** The whole mobile flow up to submit, declaring the given monthly amount range. */
	private String submitted(String rangeCode) throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()), "$.id");
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "01234567-8", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""")).andExpect(status().isOk());
		DuiStep.complete(console, requestId, "01234567-8", "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "%s"}
				""".formatted(rangeCode))).andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isOk());
		return requestId;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return console.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
