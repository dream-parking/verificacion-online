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
 * VDI-143: an escalation is a decision too; it always has its channel and analyst, and the automatic ones have neither.
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

	@Test
	void anEscalationIsLoggedWithItsChannelAndAnalystWhateverTheScore() {
		jdbc.update(ESCALATION, requestId, assessmentId(), "ESCALATED", "BRANCH", analystId());

		assertThat(jdbc.queryForObject("""
				select count(*) from risk_decision_log
				where request_id = ?::uuid and decision = 'ESCALATED' and channel = 'BRANCH'""", Integer.class, requestId))
			.isEqualTo(1);
	}

	@Test
	void anEscalationNeedsItsChannel() {
		assertThatThrownBy(() -> jdbc.update(ESCALATION, requestId, assessmentId(), "ESCALATED", null, analystId()))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_risk_decision_escalation");
	}

	@Test
	void anEscalationNeedsTheAnalystWhoDecidedIt() {
		assertThatThrownBy(() -> jdbc.update(ESCALATION, requestId, assessmentId(), "ESCALATED", "VIDEO_CALL", null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_risk_decision_escalation");
	}

	@Test
	void anAutomaticDecisionHasNoChannel() {
		assertThatThrownBy(
				() -> jdbc.update(ESCALATION, requestId, assessmentId(), "AUTOMATIC_FLOW", "VIDEO_CALL", null))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_risk_decision_escalation");
	}

	@Test
	void onlyAnEscalatedRequestHasAChannel() {
		assertThatThrownBy(() -> jdbc.update(
				"update onboarding_request set escalation_channel = 'BRANCH' where id = ?::uuid", requestId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_request_escalation_channel");
	}

	/** A decision with a score within the threshold of 69, and the given decision, channel and analyst. */
	private static final String ESCALATION = """
			insert into risk_decision_log
			  (request_id, assessment_id, decision, reason, score, threshold, model_version, channel, decided_by_user)
			values (?::uuid, ?, ?, 'Escalada', 60, 69, 1, ?, ?::uuid)""";

	/** A console user of the demo data. */
	private String analystId() {
		return jdbc.queryForObject("select id::text from console_user limit 1", String.class);
	}

	private long assessmentId() {
		return jdbc.queryForObject("select assessment_id from risk_decision_log where id = ?", Long.class, decisionId);
	}

}
