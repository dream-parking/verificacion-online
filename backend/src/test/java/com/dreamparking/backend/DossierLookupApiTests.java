package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.signature.service.ContractTerms;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-135: the archived binding dossier is found by request number or by customer number. Signing freezes it; a
 * request has one, a customer one per signed request. An unknown number answers 404 and each query is recorded in the
 * access audit.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DossierLookupApiTests {

	private static final String DOSSIERS = "/api/console/dossiers";

	private static final String DUI = "01234567-8";

	/** The simulated signature service rejects this DUI; the simulated RNPN matches it. */
	private static final String REJECTED_SIGNATURE_DUI = "00000040-8";

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	@Autowired
	ContractTerms terms;

	MockMvc console;

	@BeforeEach
	void signIn() {
		console = auth.mockMvcAs(context, auth.user(ConsoleRole.KYC_LEAD));
	}

	@Test
	void signingFreezesTheDossier() throws Exception {
		String requestId = signed(approved(DUI));
		em.flush();

		assertThat(jdbc.queryForObject("select count(*) from binding_dossier where request_id = ?::uuid", Integer.class,
				requestId))
			.isEqualTo(1);
	}

	@Test
	void aRejectedSignatureFreezesNoDossier() throws Exception {
		String requestId = approved(REJECTED_SIGNATURE_DUI);
		sign(requestId).andExpect(status().isUnprocessableContent());
		em.flush();

		assertThat(jdbc.queryForObject("select count(*) from binding_dossier where request_id = ?::uuid", Integer.class,
				requestId))
			.isZero();
	}

	@Test
	void theRequestNumberGivesTheWholeDossier() throws Exception {
		String requestId = signed(approved(DUI));
		String requestNumber = numberOf(requestId);

		perform(get(DOSSIERS).param("requestNumber", requestNumber)).andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.requestNumber").value(requestNumber))
			.andExpect(jsonPath("$.customerNumber").value(customerNumberOf(requestId)))
			.andExpect(jsonPath("$.dossier.requestId").value(requestId))
			.andExpect(jsonPath("$.dossier.signature.contractVersion").value(terms.version()))
			.andExpect(jsonPath("$.dossier.signature.contractFingerprint").value(terms.fingerprint()))
			.andExpect(jsonPath("$.dossier.retainedUntil").isNotEmpty())
			.andExpect(jsonPath("$.dossier.evidence.identity.dui").value(DUI))
			.andExpect(jsonPath("$.dossier.evidence.rnpn").isNotEmpty())
			.andExpect(jsonPath("$.dossier.evidence.risk").isNotEmpty());
	}

	@Test
	void bothNumbersGiveTheSameDossier() throws Exception {
		String requestId = signed(approved(DUI));

		Integer byRequest = JsonPath.read(perform(get(DOSSIERS).param("requestNumber", numberOf(requestId)))
			.andReturn().getResponse().getContentAsString(), "$.dossier.id");
		perform(get(DOSSIERS).param("customerNumber", customerNumberOf(requestId))).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].dossier.id").value(byRequest))
			.andExpect(jsonPath("$[0].requestNumber").value(numberOf(requestId)));
	}

	@Test
	void aCustomerHasOneDossierPerSignedRequestTheMostRecentFirst() throws Exception {
		String first = signed(approved(DUI));
		String second = signed(approved(DUI));
		String notSigned = approved(DUI);

		perform(get(DOSSIERS).param("customerNumber", customerNumberOf(first))).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].requestNumber", contains(numberOf(second), numberOf(first))))
			.andExpect(jsonPath("$[?(@.requestNumber == '%s')]".formatted(numberOf(notSigned))).isEmpty());
	}

	@Test
	void anUnknownNumberIsNotFound() throws Exception {
		perform(get(DOSSIERS).param("requestNumber", "SOL-2099-99999")).andExpect(status().isNotFound());
		perform(get(DOSSIERS).param("customerNumber", "CLI-9999999")).andExpect(status().isNotFound());
	}

	@Test
	void aRequestThatHasNotSignedHasNoDossier() throws Exception {
		String requestId = approved(DUI);

		perform(get(DOSSIERS).param("requestNumber", numberOf(requestId))).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("has not signed")));
	}

	@Test
	void aCustomerThatHasNotSignedHasAnEmptyList() throws Exception {
		String requestId = approved(DUI);

		perform(get(DOSSIERS).param("customerNumber", customerNumberOf(requestId))).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void exactlyOneWellFormedNumberIsRequired() throws Exception {
		perform(get(DOSSIERS)).andExpect(status().isBadRequest());
		perform(get(DOSSIERS).param("requestNumber", "SOL-2026-00001").param("customerNumber", "CLI-0000001"))
			.andExpect(status().isBadRequest());
		perform(get(DOSSIERS).param("requestNumber", "2026-1")).andExpect(status().isBadRequest());
		perform(get(DOSSIERS).param("customerNumber", "cliente")).andExpect(status().isBadRequest());
	}

	@Test
	void eachQueryIsRecordedInTheAccessAudit() throws Exception {
		String requestId = signed(approved(DUI));
		String requestNumber = numberOf(requestId);
		String customerNumber = customerNumberOf(requestId);

		perform(get(DOSSIERS).param("requestNumber", requestNumber)).andExpect(status().isOk());
		perform(get(DOSSIERS).param("requestNumber", requestNumber)).andExpect(status().isOk());
		perform(get(DOSSIERS).param("customerNumber", customerNumber)).andExpect(status().isOk());
		em.flush();

		assertThat(audited(requestNumber)).isEqualTo(2);
		assertThat(audited(customerNumber)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select bool_and(user_id is not null and host(ip) = '127.0.0.1' and occurred_at is not null)
				from access_audit where action = 'VIEW_BINDING_DOSSIER'""", Boolean.class))
			.isTrue();
	}

	@Test
	void everyConsoleRoleCanQueryButNotWithoutASession() throws Exception {
		String requestId = signed(approved(DUI));
		MockMvc analyst = auth.mockMvcAs(context, auth.user(ConsoleRole.FRAUD_ANALYST));
		MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context)
			.apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
			.build();

		analyst.perform(get(DOSSIERS).param("requestNumber", numberOf(requestId))).andExpect(status().isOk());
		anonymous.perform(get(DOSSIERS).param("requestNumber", numberOf(requestId)))
			.andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- helpers

	private int audited(String number) {
		return jdbc.queryForObject("""
				select count(*) from access_audit
				where action = 'VIEW_BINDING_DOSSIER' and entity_name = 'binding_dossier' and entity_id = ?""",
				Integer.class, number);
	}

	private String signed(String requestId) throws Exception {
		sign(requestId).andExpect(status().isCreated());
		return requestId;
	}

	private ResultActions sign(String requestId) throws Exception {
		return perform(post("/api/onboarding/requests/{id}/signature", requestId).content("""
				{"contractVersion": "%s"}
				""".formatted(terms.version())));
	}

	private String numberOf(String requestId) throws Exception {
		return read(perform(get("/api/console/requests/{id}", requestId)), "$.number");
	}

	private String customerNumberOf(String requestId) throws Exception {
		return read(perform(get("/api/console/requests/{id}", requestId)), "$.customerNumber");
	}

	/** The whole mobile flow up to an approved submission. */
	private String approved(String dui) throws Exception {
		String requestId = read(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()), "$.id");
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "%s", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""".formatted(dui))).andExpect(status().isOk());
		DuiStep.complete(console, requestId, dui, "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500"}
				""")).andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));
		// The customer number is assigned with SQL at submit: reload it, as a new request would.
		em.flush();
		em.clear();
		return requestId;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return console.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

}
