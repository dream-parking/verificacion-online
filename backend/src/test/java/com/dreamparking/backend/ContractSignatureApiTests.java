package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.signature.service.ContractTerms;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-130: an approved request signs the account contract once, with its evidence (version, fingerprint of the text,
 * date and time, device and IP). A second try, a request that is not approved, or a signature the simulated service
 * rejects, answers an error and stores no signature.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ContractSignatureApiTests {

	private static final String DUI = "01234567-8";

	/** The simulated RNPN does not answer for this DUI, so the request waits. */
	private static final String UNAVAILABLE_RNPN_DUI = "00000030-1";

	/** The simulated signature service rejects this DUI; the RNPN matches it, so the request is approved. */
	private static final String REJECTED_SIGNATURE_DUI = "00000040-8";

	private static final String DEVICE = "BancoApp/1.0 (Android 14; Pixel 8)";

	private static final String DEVICE_FINGERPRINT = "d4f19a3ce7b2";

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

	MockMvc app;

	@BeforeEach
	void signIn() {
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void theAppGetsTheContractInForceWithItsFingerprint() throws Exception {
		perform(get("/api/onboarding/contract"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(terms.version()))
			.andExpect(jsonPath("$.text").value(containsString("CONTRATO DE APERTURA DE CUENTA")))
			.andExpect(jsonPath("$.fingerprint").value(terms.fingerprint()));
	}

	@Test
	void anApprovedRequestSignsWithItsEvidence() throws Exception {
		String requestId = approved(DUI);

		sign(requestId, terms.version())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.requestId").value(requestId))
			.andExpect(jsonPath("$.contractVersion").value(terms.version()))
			.andExpect(jsonPath("$.contractFingerprint").value(terms.fingerprint()))
			.andExpect(jsonPath("$.signedAt").isNotEmpty())
			.andExpect(jsonPath("$.provider").value("simulated"))
			.andExpect(jsonPath("$.simulated").value(true));
		em.flush();

		assertThat(jdbc.queryForMap("""
				select contract_version, contract_fingerprint, host(ip) as ip, device, device_fingerprint,
				       signed_at is not null as dated
				from contract_signature where request_id = ?::uuid""", requestId))
			.containsEntry("contract_version", terms.version())
			.containsEntry("contract_fingerprint", terms.fingerprint())
			.containsEntry("ip", "127.0.0.1")
			.containsEntry("device", DEVICE)
			.containsEntry("device_fingerprint", DEVICE_FINGERPRINT)
			.containsEntry("dated", true);
		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.timeline[*].type").value(hasItem("CONTRACT_SIGNED")));
	}

	@Test
	void signingFreezesTheBindingDossierWithTheSignatureAndTheRetentionDate() throws Exception {
		String requestId = approved(DUI);

		sign(requestId, terms.version()).andExpect(status().isCreated());
		em.flush();

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.bindingDossier.signature.contractVersion").value(terms.version()))
			.andExpect(jsonPath("$.bindingDossier.signature.contractFingerprint").value(terms.fingerprint()))
			.andExpect(jsonPath("$.bindingDossier.signature.signedAt").isNotEmpty())
			.andExpect(jsonPath("$.bindingDossier.retainedUntil").isNotEmpty())
			.andExpect(jsonPath("$.bindingDossier.evidence.identity.dui").value(DUI))
			.andExpect(jsonPath("$.bindingDossier.evidence.risk.assessment.signals").isNotEmpty());
		assertThat(jdbc.queryForObject("select count(*) from binding_dossier where request_id = ?::uuid", Integer.class,
				requestId))
			.isEqualTo(1);
	}

	@Test
	void aSignatureTheServiceRejectsFreezesNoDossier() throws Exception {
		String requestId = approved(REJECTED_SIGNATURE_DUI);

		sign(requestId, terms.version()).andExpect(status().isUnprocessableEntity());
		em.flush();

		assertThat(jdbc.queryForObject("select count(*) from binding_dossier where request_id = ?::uuid", Integer.class,
				requestId))
			.isZero();
	}

	@Test
	void aSecondTryIsRefusedAndTheFirstSignatureStays() throws Exception {
		String requestId = approved(DUI);
		sign(requestId, terms.version()).andExpect(status().isCreated());

		sign(requestId, terms.version())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("already signed")));
		assertThat(signaturesOf(requestId)).isEqualTo(1);
	}

	@Test
	void aRequestInProgressCannotSign() throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));

		sign(requestId, terms.version())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("Only submitted and approved requests")));
		assertThat(signaturesOf(requestId)).isZero();
	}

	@Test
	void anAbandonedRequestCannotSign() throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(post("/api/onboarding/requests/{id}/abandon", requestId)).andExpect(status().isOk());

		sign(requestId, terms.version()).andExpect(status().isConflict());
		assertThat(signaturesOf(requestId)).isZero();
	}

	@Test
	void aRequestHeldForEnhancedReviewCannotSign() throws Exception {
		strictModelInForce();
		String requestId = idOf(submitted(DUI, "MAS_1000").andExpect(jsonPath("$.status").value("ENHANCED_REVIEW")));

		sign(requestId, terms.version())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("held for enhanced review")));
		assertThat(signaturesOf(requestId)).isZero();
	}

	@Test
	void aRequestWaitingForTheRnpnCannotSign() throws Exception {
		String requestId = idOf(submitted(UNAVAILABLE_RNPN_DUI, "200_500")
			.andExpect(jsonPath("$.status").value("AWAITING_RNPN")));

		sign(requestId, terms.version())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("waiting for the RNPN")));
		assertThat(signaturesOf(requestId)).isZero();
	}

	@Test
	void aContractVersionThatIsNotInForceIsRefused() throws Exception {
		String requestId = approved(DUI);

		sign(requestId, "2019.1")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(containsString("not the one in force")));
		assertThat(signaturesOf(requestId)).isZero();
	}

	@Test
	void theContractVersionIsRequired() throws Exception {
		String requestId = approved(DUI);

		perform(post("/api/onboarding/requests/{id}/signature", requestId).content("""
				{"contractVersion": " "}
				""")).andExpect(status().isBadRequest());
		assertThat(signaturesOf(requestId)).isZero();
	}

	@Test
	void anUnknownRequestIsNotFound() throws Exception {
		sign(UUID.randomUUID().toString(), terms.version()).andExpect(status().isNotFound());
	}

	@Test
	void aSignatureTheServiceRejectsIsNotStoredButLeavesItsEvent() throws Exception {
		String requestId = approved(REJECTED_SIGNATURE_DUI);

		sign(requestId, terms.version())
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.detail").value(containsString("The signature was rejected")));
		em.flush();

		assertThat(signaturesOf(requestId)).isZero();
		assertThat(jdbc.queryForObject("""
				select count(*) from request_event where request_id = ?::uuid and type = 'CONTRACT_SIGNATURE_REJECTED'""",
				Integer.class, requestId))
			.isEqualTo(1);
	}

	/** A model in force whose threshold is 2, so any request with an amount above 200 goes over it. */
	private void strictModelInForce() {
		jdbc.update("update score_model set valid_to = now() where valid_to is null");
		jdbc.update("""
				insert into score_model (version, name, low_max, medium_max, valid_from)
				values (99, 'Strict test model', 1, 2, now())""");
		jdbc.update("insert into score_model_weight (model_version, signal, max_points) values (99, 'AMOUNT', 25)");
	}

	private String approved(String dui) throws Exception {
		return idOf(submitted(dui, "200_500").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")));
	}

	/** The whole mobile flow up to submit, with the given DUI and monthly amount range, and the device signals. */
	private ResultActions submitted(String dui, String rangeCode) throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(status().isOk());
		perform(put("/api/onboarding/requests/{id}/signals", requestId).content("""
				{"deviceFingerprint": "%s", "deviceModel": "Pixel 8", "operatingSystem": "Android"}
				""".formatted(DEVICE_FINGERPRINT))).andExpect(status().is2xxSuccessful());
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "%s", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""".formatted(dui))).andExpect(status().isOk());
		DuiStep.complete(app, requestId, dui, "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "%s"}
				""".formatted(rangeCode))).andExpect(status().isOk());
		return perform(post("/api/onboarding/requests/{id}/submit", requestId));
	}

	private ResultActions sign(String requestId, String contractVersion) throws Exception {
		return perform(post("/api/onboarding/requests/{id}/signature", requestId).header(HttpHeaders.USER_AGENT, DEVICE)
			.content("""
					{"contractVersion": "%s"}
					""".formatted(contractVersion)));
	}

	private int signaturesOf(String requestId) {
		return jdbc.queryForObject("select count(*) from contract_signature where request_id = ?::uuid", Integer.class,
				requestId);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return app.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
