package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * VDI-131 and VDI-134: the binding dossier of a request is kept as it was frozen: not even SQL can change it, and it cannot
 * be deleted (nor its request) before its retention date.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableBindingDossierTests {

	private static final String FINGERPRINT = "0123456789abcdef".repeat(4);

	@Autowired
	JdbcTemplate jdbc;

	String requestId;

	long dossierId;

	@BeforeEach
	void signedRequest() {
		requestId = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		dossierId = jdbc.queryForObject("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, retained_until, evidence)
				values (?::uuid, '2026.1', ?, now() - interval '1 day', now() + interval '10 years',
				        '{"schemaVersion": 1}'::jsonb) returning id""",
				Long.class, requestId, FINGERPRINT);
	}

	@Test
	void theEvidenceCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update binding_dossier set evidence = '{\"schemaVersion\": 2}' where id = ?",
				dossierId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified");
	}

	@Test
	void theSignatureCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update binding_dossier set contract_version = '2026.2' where id = ?",
				dossierId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified");
	}

	@Test
	void aDossierWithinItsRetentionPeriodCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from binding_dossier where id = ?", dossierId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be deleted before");
	}

	@Test
	void aDossierCannotBeDeletedEvenOneMicrosecondBeforeItsRetentionDate() {
		long almostExpired = dossierRetainedUntil("now() + interval '1 microsecond'");

		assertThatThrownBy(() -> jdbc.update("delete from binding_dossier where id = ?", almostExpired))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be deleted before");
	}

	@Test
	void aDossierCanBeDeletedFromTheMomentItsRetentionDateIsReached() {
		long expired = dossierRetainedUntil("now()");

		assertThat(jdbc.update("delete from binding_dossier where id = ?", expired)).isEqualTo(1);
	}

	@Test
	void aDossierCanBeDeletedOnceItsRetentionDateHasPassed() {
		long expired = dossierRetainedUntil("now() - interval '1 microsecond'");

		assertThat(jdbc.update("delete from binding_dossier where id = ?", expired)).isEqualTo(1);
	}

	@Test
	void aDossierPastItsRetentionDateStillCannotBeEdited() {
		long expired = dossierRetainedUntil("now() - interval '1 day'");

		assertThatThrownBy(() -> jdbc.update("update binding_dossier set retained_until = now() + interval '5 years' where id = ?",
				expired))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified");
	}

	@Test
	void theRetentionDateMustBeAfterTheSignature() {
		String otherRequest = jdbc.queryForObject("insert into onboarding_request default values returning id::text",
				String.class);

		assertThatThrownBy(() -> jdbc.update("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, retained_until, evidence)
				values (?::uuid, '2026.1', ?, now(), now(), '{}'::jsonb)""", otherRequest, FINGERPRINT))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_binding_dossier_retention");
	}

	@Test
	void aRequestHasOneDossier() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, retained_until, evidence)
				values (?::uuid, '2026.1', ?, now(), now() + interval '1 year', '{}'::jsonb)""", requestId, FINGERPRINT))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theFingerprintMustBeASha256InLowercaseHexadecimal() {
		String otherRequest = jdbc.queryForObject("insert into onboarding_request default values returning id::text",
				String.class);

		assertThatThrownBy(() -> insert(otherRequest, "ABCDEF".repeat(10) + "abcd", "{}"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_binding_dossier_fingerprint");
	}

	@Test
	void aShortFingerprintIsRefused() {
		String otherRequest = jdbc.queryForObject("insert into onboarding_request default values returning id::text",
				String.class);

		assertThatThrownBy(() -> insert(otherRequest, "abc", "{}"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_binding_dossier_fingerprint");
	}

	@Test
	void theEvidenceMustBeAJsonObject() {
		String otherRequest = jdbc.queryForObject("insert into onboarding_request default values returning id::text",
				String.class);

		assertThatThrownBy(() -> insert(otherRequest, FINGERPRINT, "[1, 2]"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_binding_dossier_evidence");
	}

	@Test
	void aRequestWithADossierWithinItsPeriodCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from onboarding_request where id = ?::uuid", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be deleted before");
	}

	@Test
	void deletingTheWholeRequestRemovesItsDossierOnceThePeriodHasPassed() {
		String expiredRequest = jdbc.queryForObject("insert into onboarding_request default values returning id::text",
				String.class);
		long expired = jdbc.queryForObject("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, retained_until, evidence)
				values (?::uuid, '2026.1', ?, now() - interval '11 years', now() - interval '1 day', '{}'::jsonb) returning id""",
				Long.class, expiredRequest, FINGERPRINT);

		jdbc.update("delete from onboarding_request where id = ?::uuid", expiredRequest);

		assertThat(jdbc.queryForObject("select count(*) from binding_dossier where id = ?", Integer.class, expired))
			.isZero();
	}

	/** A dossier of another request whose retention date is this SQL expression (now() is fixed inside the test). */
	private long dossierRetainedUntil(String retainedUntil) {
		String otherRequest = jdbc.queryForObject("insert into onboarding_request default values returning id::text",
				String.class);
		return jdbc.queryForObject("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, retained_until, evidence)
				values (?::uuid, '2026.1', ?, now() - interval '20 years', %s, '{}'::jsonb) returning id""".formatted(retainedUntil),
				Long.class, otherRequest, FINGERPRINT);
	}

	private void insert(String request, String fingerprint, String evidence) {
		jdbc.update("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, retained_until, evidence)
				values (?::uuid, '2026.1', ?, now(), now() + interval '1 year', ?::jsonb)""", request, fingerprint, evidence);
	}

}
