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
 * VDI-111: the RNPN checks of a request only receive new rows. Not even SQL can change or delete one, and the database
 * refuses a row that does not make sense (a mismatch without fields, fields without a mismatch, a failure on a match).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableRnpnCheckTests {

	@Autowired
	JdbcTemplate jdbc;

	String requestId;

	long checkId;

	@BeforeEach
	void checkedRequest() {
		requestId = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		checkId = jdbc.queryForObject("""
				insert into rnpn_check (request_id, outcome, provider, attempts, mismatched_fields)
				values (?::uuid, 'NO_MATCH', 'simulated', 1, '{LAST_NAMES,BIRTH_DATE}') returning id""", Long.class,
				requestId);
	}

	@Test
	void theCheckIsKeptWithItsDateTimeAndAttempts() {
		assertThat(jdbc.queryForObject("select checked_at is not null from rnpn_check where id = ?", Boolean.class, checkId))
			.isTrue();
		assertThat(jdbc.queryForObject("select attempts from rnpn_check where id = ?", Integer.class, checkId)).isEqualTo(1);
	}

	@Test
	void aCheckCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update rnpn_check set outcome = 'MATCH', mismatched_fields = '{}' where id = ?",
				checkId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void aCheckCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from rnpn_check where id = ?", checkId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void deletingTheRequestStillCascadesToItsChecks() {
		jdbc.update("delete from onboarding_request where id = ?::uuid", requestId);

		assertThat(jdbc.queryForObject("select count(*) from rnpn_check where id = ?", Integer.class, checkId)).isZero();
	}

	@Test
	void aRequestCanHaveSeveralChecks() {
		jdbc.update("""
				insert into rnpn_check (request_id, outcome, provider, attempts, failure)
				values (?::uuid, 'UNAVAILABLE', 'simulated', 3, 'timeout')""", requestId);

		assertThat(jdbc.queryForObject("select count(*) from rnpn_check where request_id = ?::uuid", Integer.class,
				requestId))
			.isEqualTo(2);
	}

	@Test
	void aMismatchMustNameTheFields() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into rnpn_check (request_id, outcome, provider, attempts) values (?::uuid, 'NO_MATCH', 'simulated', 1)""",
				requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_rnpn_check_mismatch");
	}

	@Test
	void aMatchCannotNameFields() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into rnpn_check (request_id, outcome, provider, attempts, mismatched_fields)
				values (?::uuid, 'MATCH', 'simulated', 1, '{DUI}')""", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_rnpn_check_mismatch");
	}

	@Test
	void onlyAnUnavailableCheckCanNameAFailure() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into rnpn_check (request_id, outcome, provider, attempts, failure)
				values (?::uuid, 'MATCH', 'simulated', 1, 'timeout')""", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_rnpn_check_failure");
	}

	@Test
	void anUnknownFieldIsRefused() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into rnpn_check (request_id, outcome, provider, attempts, mismatched_fields)
				values (?::uuid, 'NO_MATCH', 'simulated', 1, '{PASSPORT}')""", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_rnpn_check_fields");
	}

	@Test
	void theCheckNeedsAtLeastOneTry() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into rnpn_check (request_id, outcome, provider, attempts) values (?::uuid, 'MATCH', 'simulated', 0)""",
				requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_rnpn_check_attempts");
	}

}
