package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.dossier.dto.BindingDossierResponse;
import com.dreamparking.backend.dossier.dto.BindingDossierResponse.Signature;
import com.dreamparking.backend.dossier.service.BindingDossierService;
import com.jayway.jsonpath.JsonPath;

/**
 * VDI-131: when the customer signs, the evidence that backed the approval is frozen in the binding dossier and the
 * console reads it with the request. It says which signals backed the approval and when, even if the score model and its
 * thresholds change afterwards. The signature (VDI-130) calls {@link BindingDossierService#freeze}; here the tests call
 * it directly.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class BindingDossierApiTests {

	private static final String FINGERPRINT = "a".repeat(64);

	private static final Instant SIGNED_AT = Instant.parse("2026-10-27T15:30:00Z");

	@Autowired
	WebApplicationContext context;

	@Autowired
	TestAuth auth;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	EntityManager em;

	@Autowired
	BindingDossierService dossiers;

	MockMvc app;

	@BeforeEach
	void signIn() {
		app = auth.mockMvcAs(context, auth.user(ConsoleRole.ADMIN));
	}

	@Test
	void theDossierFreezesWhatBackedTheApprovalWithTheDateOfEachElement() throws Exception {
		String requestId = submitted("01234567-8");

		BindingDossierResponse dossier = dossiers.freeze(UUID.fromString(requestId), signature());

		assertThat(dossier.requestId()).hasToString(requestId);
		assertThat(dossier.signature()).isEqualTo(signature());
		assertThat(dossier.frozenAt()).isNotNull();
		var evidence = dossier.evidence();
		assertThat(evidence.schemaVersion()).isEqualTo(1);
		assertThat(evidence.identity().dui()).isEqualTo("01234567-8");
		assertThat(evidence.identity().firstNames()).isEqualTo("Ana Sofía");
		assertThat(evidence.identity().customerNumber()).startsWith("CLI-");
		assertThat(evidence.identity().confirmedAt()).isNotNull();
		assertThat(evidence.consent().noticeVersion()).isNotBlank();
		assertThat(evidence.consent().acceptedAt()).isNotNull();
		assertThat(evidence.rnpn().outcome().name()).isEqualTo("MATCH");
		assertThat(evidence.rnpn().checkedAt()).isNotNull();
		assertThat(evidence.liveness()).isNull();
		assertThat(evidence.risk().assessment().score()).isNotNull();
		assertThat(evidence.risk().assessment().signals()).isNotEmpty();
		assertThat(evidence.risk().assessment().evaluatedAt()).isNotNull();
		assertThat(evidence.risk().assessment().modelVersion()).isNotNull();
		assertThat(evidence.risk().lowMax()).isNotNull();
		assertThat(evidence.risk().reviewThreshold()).isEqualTo(evidence.risk().mediumMax());
		assertThat(evidence.decisions()).hasSize(1);
		assertThat(evidence.decisions().get(0).decidedAt()).isNotNull();
	}

	@Test
	void theConsoleDeliversTheDossierWithTheRequestOnceSigned() throws Exception {
		String requestId = submitted("01234567-8");
		perform(get("/api/console/requests/{id}", requestId)).andExpect(jsonPath("$.bindingDossier").value(nullValue()));
		dossiers.freeze(UUID.fromString(requestId), signature());
		em.flush();

		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.bindingDossier.requestId").value(requestId))
			.andExpect(jsonPath("$.bindingDossier.signature.contractVersion").value("2026.1"))
			.andExpect(jsonPath("$.bindingDossier.signature.contractFingerprint").value(FINGERPRINT))
			.andExpect(jsonPath("$.bindingDossier.signature.signedAt").value("2026-10-27T15:30:00Z"))
			.andExpect(jsonPath("$.bindingDossier.evidence.identity.dui").value("01234567-8"))
			.andExpect(jsonPath("$.bindingDossier.evidence.consent.noticeVersion").isNotEmpty())
			.andExpect(jsonPath("$.bindingDossier.evidence.rnpn.outcome").value("MATCH"))
			.andExpect(jsonPath("$.bindingDossier.evidence.risk.assessment.signals[0].signal").isNotEmpty())
			.andExpect(jsonPath("$.bindingDossier.evidence.risk.assessment.signals[0].observation").isNotEmpty())
			.andExpect(jsonPath("$.bindingDossier.evidence.decisions[0].decision").value("AUTOMATIC_FLOW"));
	}

	@Test
	void theDossierKeepsTheScoreAndTheThresholdsEvenIfTheModelChangesAfterwards() throws Exception {
		String requestId = submitted("01234567-8");
		BindingDossierResponse frozen = dossiers.freeze(UUID.fromString(requestId), signature());
		em.flush();

		jdbc.update("update score_model set valid_to = now() where valid_to is null");
		jdbc.update("""
				insert into score_model (version, name, low_max, medium_max, valid_from)
				values (99, 'Strict test model', 1, 2, now())""");
		jdbc.update("insert into score_model_weight (model_version, signal, max_points) values (99, 'AMOUNT', 5)");
		em.clear();

		BindingDossierResponse later = dossiers.find(UUID.fromString(requestId)).orElseThrow();
		assertThat(later.evidence()).isEqualTo(frozen.evidence());
		assertThat(later.evidence().risk().mediumMax()).isNotEqualTo((short) 2);
		perform(get("/api/console/requests/{id}", requestId))
			.andExpect(jsonPath("$.bindingDossier.evidence.risk.assessment.score")
				.value(frozen.evidence().risk().assessment().score()))
			.andExpect(jsonPath("$.bindingDossier.evidence.risk.mediumMax").value(frozen.evidence().risk().mediumMax().intValue()));
	}

	@Test
	void signingTwiceKeepsTheFirstDossier() throws Exception {
		UUID requestId = UUID.fromString(submitted("01234567-8"));
		BindingDossierResponse first = dossiers.freeze(requestId, signature());

		BindingDossierResponse second = dossiers.freeze(requestId,
				new Signature("2026.2", "b".repeat(64), SIGNED_AT.plusSeconds(60)));
		em.flush();

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(second.signature()).isEqualTo(first.signature());
		assertThat(jdbc.queryForObject("select count(*) from binding_dossier where request_id = ?", Integer.class,
				requestId))
			.isEqualTo(1);
	}

	@Test
	void aRequestWithoutEvidenceFreezesOnlyWhatItHas() throws Exception {
		UUID requestId = UUID.fromString(idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated())));

		var evidence = dossiers.freeze(requestId, signature()).evidence();

		assertThat(evidence.identity()).isNull();
		assertThat(evidence.consent()).isNull();
		assertThat(evidence.rnpn()).isNull();
		assertThat(evidence.liveness()).isNull();
		assertThat(evidence.risk()).isNull();
		assertThat(evidence.decisions()).isEmpty();
	}

	@Test
	void aRequestThatDoesNotExistHasNoDossier() {
		UUID unknown = UUID.randomUUID();

		assertThatThrownBy(() -> dossiers.freeze(unknown, signature())).isInstanceOf(NotFoundException.class);
		assertThat(dossiers.find(unknown)).isEmpty();
	}

	@Test
	void theSignatureNeedsTheContractVersionTheFingerprintAndTheDate() {
		UUID requestId = UUID.randomUUID();

		assertThatThrownBy(() -> dossiers.freeze(requestId, null)).isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> dossiers.freeze(requestId, new Signature(" ", FINGERPRINT, SIGNED_AT)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> dossiers.freeze(requestId, new Signature("v".repeat(31), FINGERPRINT, SIGNED_AT)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> dossiers.freeze(requestId, new Signature(null, FINGERPRINT, SIGNED_AT)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> dossiers.freeze(requestId, new Signature("2026.1", FINGERPRINT, null)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> dossiers.freeze(requestId, new Signature("2026.1", "abc", SIGNED_AT)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> dossiers.freeze(requestId, new Signature("2026.1", FINGERPRINT.toUpperCase(), SIGNED_AT)))
			.isInstanceOf(InvalidInputException.class);
		assertThatThrownBy(() -> dossiers.freeze(requestId, new Signature("2026.1", null, SIGNED_AT)))
			.isInstanceOf(InvalidInputException.class);
	}

	@Test
	void theContractVersionCanHaveTheMaximumLength() throws Exception {
		UUID requestId = UUID.fromString(submitted("01234567-8"));

		BindingDossierResponse dossier = dossiers.freeze(requestId, new Signature("v".repeat(30), FINGERPRINT, SIGNED_AT));

		assertThat(dossier.signature().contractVersion()).hasSize(30);
	}

	private static Signature signature() {
		return new Signature("2026.1", FINGERPRINT, SIGNED_AT);
	}

	/** The whole mobile flow up to submit; returns the id of the request. */
	private String submitted(String dui) throws Exception {
		String requestId = idOf(perform(post("/api/onboarding/requests")).andExpect(status().isCreated()));
		perform(put("/api/onboarding/requests/{id}/privacy-consent", requestId).content("""
				{"signalsAccepted": true}
				""")).andExpect(jsonPath("$.completedSteps").value(1));
		perform(put("/api/onboarding/requests/{id}/basic-data", requestId).content("""
				{"dui": "%s", "firstNames": "Ana Sofía", "lastNames": "Pérez López", "mobilePhone": "7123-4567"}
				""".formatted(dui))).andExpect(jsonPath("$.completedSteps").value(2));
		DuiStep.complete(app, requestId, dui, "Ana Sofía", "Pérez López");
		perform(put("/api/onboarding/requests/{id}/income", requestId).content("""
				{"sourceCode": "SALARIO", "rangeCode": "HASTA_500"}
				""")).andExpect(status().isNoContent());
		perform(put("/api/onboarding/requests/{id}/expected-activity", requestId).content("""
				{"transactionTypeCode": "PAGO_SALARIO", "monthlyAmountRangeCode": "200_500"}
				""")).andExpect(status().isOk());
		perform(post("/api/onboarding/requests/{id}/submit", requestId)).andExpect(status().isOk());
		// The customer number is given with a native update: read it from the database, as the signature will.
		em.flush();
		em.clear();
		return requestId;
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return app.perform(request.contentType(MediaType.APPLICATION_JSON));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

}
