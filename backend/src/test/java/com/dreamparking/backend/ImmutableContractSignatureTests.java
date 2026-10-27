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
 * VDI-130: the signature of a contract only receives new rows, one per request. Not even SQL can change or delete one,
 * and the database refuses a fingerprint that is not a SHA-256 in hex.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableContractSignatureTests {

	private static final String FINGERPRINT = "a".repeat(64);

	@Autowired
	JdbcTemplate jdbc;

	String requestId;

	long signatureId;

	@BeforeEach
	void signedRequest() {
		requestId = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		signatureId = jdbc.queryForObject("""
				insert into contract_signature (request_id, contract_version, contract_fingerprint, provider, ip, device)
				values (?::uuid, '2026.1', ?, 'simulated', '10.0.0.7', 'okhttp/4.12') returning id""", Long.class,
				requestId, FINGERPRINT);
	}

	@Test
	void theSignatureIsKeptWithItsEvidence() {
		assertThat(jdbc.queryForObject("""
				select signed_at is not null and host(ip) = '10.0.0.7' and device = 'okhttp/4.12'
				from contract_signature where id = ?""", Boolean.class, signatureId))
			.isTrue();
	}

	@Test
	void aSignatureCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update contract_signature set contract_version = '2027.1' where id = ?",
				signatureId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void aSignatureCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from contract_signature where id = ?", signatureId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void deletingTheRequestStillCascadesToItsSignature() {
		jdbc.update("delete from onboarding_request where id = ?::uuid", requestId);

		assertThat(jdbc.queryForObject("select count(*) from contract_signature where id = ?", Integer.class, signatureId))
			.isZero();
	}

	@Test
	void aRequestHasOnlyOneSignature() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into contract_signature (request_id, contract_version, contract_fingerprint, provider)
				values (?::uuid, '2026.1', ?, 'simulated')""", requestId, FINGERPRINT))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theFingerprintMustBeASha256InHex() {
		String other = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);

		assertThatThrownBy(() -> jdbc.update("""
				insert into contract_signature (request_id, contract_version, contract_fingerprint, provider)
				values (?::uuid, '2026.1', 'not-a-hash', 'simulated')""", other))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_contract_signature_fingerprint");
	}

	@Test
	void theContractVersionCannotBeBlank() {
		String other = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);

		assertThatThrownBy(() -> jdbc.update("""
				insert into contract_signature (request_id, contract_version, contract_fingerprint, provider)
				values (?::uuid, '  ', ?, 'simulated')""", other, FINGERPRINT))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_contract_signature_version");
	}

}
