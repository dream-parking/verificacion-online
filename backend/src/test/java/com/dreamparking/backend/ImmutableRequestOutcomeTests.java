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

import com.dreamparking.backend.console.entity.enums.ConsoleRole;

/**
 * VDI-144: the outcomes of a case are insert-only. Not even SQL can change or delete one, nor the link that says which
 * risk list entries an outcome touched; only deleting the whole request takes them away.
 */
@Import({ TestcontainersConfiguration.class, TestAuth.class })
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableRequestOutcomeTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TestAuth auth;

	String requestId;

	String userId;

	long outcomeId;

	long linkId;

	@BeforeEach
	void anOutcomeWithAListedEntry() {
		requestId = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		userId = auth.user(ConsoleRole.FRAUD_ANALYST).getId().toString();
		outcomeId = jdbc.queryForObject("""
				insert into request_outcome (request_id, outcome, reason, registered_by)
				values (?::uuid, 'FRAUDE_CONFIRMADO', 'Fraude confirmado', ?::uuid) returning id""", Long.class, requestId,
				userId);
		long entryId = jdbc.queryForObject("""
				insert into risk_list_entry (list_id, data_type, value_hash, reason, added_by)
				values ((select id from risk_list where code = 'FRAUDE_CONFIRMADO'), 'DOCUMENT', repeat('a', 64), 'Fraude', ?::uuid)
				returning id""", Long.class, userId);
		linkId = jdbc.queryForObject(
				"insert into request_outcome_entry (outcome_id, entry_id, link) values (?, ?, 'LISTED') returning id",
				Long.class, outcomeId, entryId);
	}

	@Test
	void theOutcomeIsKeptWithWhoAndWhen() {
		assertThat(jdbc.queryForObject("select registered_at is not null from request_outcome where id = ?", Boolean.class,
				outcomeId))
			.isTrue();
	}

	@Test
	void anOutcomeCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update request_outcome set outcome = 'FALSO_POSITIVO' where id = ?", outcomeId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void anOutcomeCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from request_outcome where id = ?", outcomeId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theLinkToAnEntryCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update request_outcome_entry set link = 'REVOKED' where id = ?", linkId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void theLinkToAnEntryCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from request_outcome_entry where id = ?", linkId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void anOutcomeThatIsNeitherFraudNorFalsePositiveIsRefused() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into request_outcome (request_id, outcome, reason, registered_by)
				values (?::uuid, 'SOSPECHOSO', 'x', ?::uuid)""", requestId, userId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_request_outcome");
	}

	@Test
	void anEntryIsListedAndRevokedAtMostOnce() {
		long entryId = jdbc.queryForObject("select entry_id from request_outcome_entry where id = ?", Long.class, linkId);

		assertThatThrownBy(() -> jdbc.update(
				"insert into request_outcome_entry (outcome_id, entry_id, link) values (?, ?, 'LISTED')", outcomeId, entryId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_request_outcome_entry");
	}

	@Test
	void deletingTheRequestCascadesToItsOutcomesButTheEntryStays() {
		jdbc.update("delete from onboarding_request where id = ?::uuid", requestId);

		assertThat(jdbc.queryForObject("select count(*) from request_outcome where id = ?", Integer.class, outcomeId))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from request_outcome_entry where id = ?", Integer.class, linkId))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from risk_list_entry", Integer.class)).isEqualTo(1);
	}

}
