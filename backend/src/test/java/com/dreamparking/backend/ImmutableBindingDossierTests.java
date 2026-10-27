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
 * VDI-131: the binding dossier of a request is kept as it was frozen; not even SQL can change or delete it. The only
 * deletion allowed is the cascade of deleting the whole request, like the rest of the file.
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
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, evidence)
				values (?::uuid, '2026.1', ?, '2026-10-27T15:30:00Z', '{"schemaVersion": 1}'::jsonb) returning id""",
				Long.class, requestId, FINGERPRINT);
	}

	@Test
	void theEvidenceCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update binding_dossier set evidence = '{\"schemaVersion\": 2}' where id = ?",
				dossierId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theSignatureCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update binding_dossier set contract_version = '2026.2' where id = ?",
				dossierId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void aDossierCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from binding_dossier where id = ?", dossierId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void aRequestHasOneDossier() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, evidence)
				values (?::uuid, '2026.1', ?, now(), '{}'::jsonb)""", requestId, FINGERPRINT))
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
	void deletingTheWholeRequestRemovesItsDossier() {
		jdbc.update("delete from onboarding_request where id = ?::uuid", requestId);

		assertThat(jdbc.queryForObject("select count(*) from binding_dossier where id = ?", Integer.class, dossierId))
			.isZero();
	}

	private void insert(String request, String fingerprint, String evidence) {
		jdbc.update("""
				insert into binding_dossier (request_id, contract_version, contract_fingerprint, signed_at, evidence)
				values (?::uuid, '2026.1', ?, now(), ?::jsonb)""", request, fingerprint, evidence);
	}

}
