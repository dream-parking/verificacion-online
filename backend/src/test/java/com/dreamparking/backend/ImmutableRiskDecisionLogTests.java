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
 * VDI-117: the audit log of risk decisions is append-only. Not even SQL can change or delete a decision, and the
 * database refuses a decision that does not follow the rule (held if and only if the score goes above the threshold).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableRiskDecisionLogTests {

	@Autowired
	JdbcTemplate jdbc;

	String requestId;

	long decisionId;

	@BeforeEach
	void decidedRequest() {
		requestId = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		long assessmentId = jdbc.queryForObject("""
				insert into risk_assessment (request_id, level, explanation, score, model_version)
				values (?::uuid, 'HIGH', 'Score 80 de 100', 80, 1) returning id""", Long.class, requestId);
		decisionId = jdbc.queryForObject("""
				insert into risk_decision_log (request_id, assessment_id, decision, reason, score, threshold, model_version)
				values (?::uuid, ?, 'ENHANCED_REVIEW', 'Score 80 is above the threshold of 69', 80, 69, 1) returning id""",
				Long.class, requestId, assessmentId);
	}

	@Test
	void theDecisionIsKeptWithItsDateAndWhoTookIt() {
		assertThat(jdbc.queryForObject("select decided_by from risk_decision_log where id = ?", String.class, decisionId))
			.isEqualTo("SYSTEM");
		assertThat(jdbc.queryForObject("select decided_at is not null from risk_decision_log where id = ?", Boolean.class,
				decisionId))
			.isTrue();
	}

	@Test
	void aDecisionCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update risk_decision_log set reason = 'edited' where id = ?", decisionId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void aDecisionCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from risk_decision_log where id = ?", decisionId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void deletingTheRequestStillCascadesToItsDecisions() {
		jdbc.update("delete from onboarding_request where id = ?::uuid", requestId);

		assertThat(jdbc.queryForObject("select count(*) from risk_decision_log where id = ?", Integer.class, decisionId))
			.isZero();
	}

	@Test
	void aScoreAboveTheThresholdCannotBeLoggedAsLetThrough() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into risk_decision_log (request_id, assessment_id, decision, reason, score, threshold, model_version)
				values (?::uuid, ?, 'AUTOMATIC_FLOW', 'let through', 80, 69, 1)""", requestId, assessmentId()))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_risk_decision_rule");
	}

	@Test
	void aScoreWithinTheThresholdCannotBeLoggedAsHeld() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into risk_decision_log (request_id, assessment_id, decision, reason, score, threshold, model_version)
				values (?::uuid, ?, 'ENHANCED_REVIEW', 'held', 69, 69, 1)""", requestId, assessmentId()))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_risk_decision_rule");
	}

	private long assessmentId() {
		return jdbc.queryForObject("select assessment_id from risk_decision_log where id = ?", Long.class, decisionId);
	}

}
