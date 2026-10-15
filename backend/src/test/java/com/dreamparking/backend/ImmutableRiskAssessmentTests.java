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
 * VDI-83: a risk assessment and what each signal contributed stay in the file as they were scored, not even SQL can
 * change or delete them. The only change allowed is a newer assessment retiring the current one.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableRiskAssessmentTests {

	@Autowired
	JdbcTemplate jdbc;

	String requestId;

	long assessmentId;

	@BeforeEach
	void scoredRequest() {
		requestId = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		assessmentId = jdbc.queryForObject("""
				insert into risk_assessment (request_id, level, explanation, score, model_version)
				values (?::uuid, 'LOW', 'Score 8 de 100', 8, 1) returning id""", Long.class, requestId);
		jdbc.update("""
				insert into risk_assessment_signal (assessment_id, signal, points, max_points, observation)
				values (?, 'AMOUNT', 8, 25, 'Monto mensual declarado: USD 500.01 a 1,000')""", assessmentId);
	}

	@Test
	void theScoreAndTheLevelCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update risk_assessment set score = 90, level = 'HIGH' where id = ?",
				assessmentId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified");
	}

	@Test
	void anAssessmentCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from risk_assessment where id = ?", assessmentId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be deleted");
	}

	@Test
	void whatASignalContributedCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update risk_assessment_signal set points = 0 where assessment_id = ?",
				assessmentId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void whatASignalContributedCannotBeDeleted() {
		assertThatThrownBy(() -> jdbc.update("delete from risk_assessment_signal where assessment_id = ?", assessmentId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void aNewerAssessmentCanRetireTheCurrentOneButNotBringItBack() {
		int retired = jdbc.update("update risk_assessment set is_current = false where id = ?", assessmentId);

		assertThat(retired).isEqualTo(1);
		assertThatThrownBy(() -> jdbc.update("update risk_assessment set is_current = true where id = ?", assessmentId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void retiringCannotSneakInOtherChanges() {
		assertThatThrownBy(() -> jdbc.update("update risk_assessment set is_current = false, score = 0 where id = ?",
				assessmentId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingTheRequestStillCascadesToItsAssessments() {
		jdbc.update("delete from onboarding_request where id = ?::uuid", requestId);

		assertThat(jdbc.queryForObject("select count(*) from risk_assessment where id = ?", Integer.class, assessmentId))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from risk_assessment_signal where assessment_id = ?",
				Integer.class, assessmentId))
			.isZero();
	}

}
