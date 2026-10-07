package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** VDI-23: once a request leaves EN_PROGRESO its declarations cannot be edited or deleted, not even with SQL. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableDeclarationTests {

	private static final String SUBMITTED = "00000000-0000-0000-0000-000000000418";

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void submittedIncomeDeclarationCannotBeEdited() {
		assertThatThrownBy(() -> jdbc.update(
				"update income_declaration set range_code = 'MAS_2500' where request_id = ?::uuid", SUBMITTED))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be modified or deleted");
	}

	@Test
	void submittedIncomeDeclarationCannotBeDeleted() {
		assertThatThrownBy(
				() -> jdbc.update("delete from income_declaration where request_id = ?::uuid", SUBMITTED))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void submittedExpectedActivityCannotBeEditedOrDeleted() {
		assertThatThrownBy(() -> jdbc.update(
				"update expected_activity set monthly_amount_range_code = 'MAS_1000' where request_id = ?::uuid",
				SUBMITTED))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void declarationsOfARequestInProgressCanStillBeCorrected() {
		String id = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		jdbc.update("insert into income_declaration (request_id, source_code, range_code)"
				+ " values (?::uuid, 'SALARIO', 'HASTA_500')", id);

		int updated = jdbc.update("update income_declaration set range_code = '500_1000' where request_id = ?::uuid",
				id);

		assertThat(updated).isEqualTo(1);
	}

	@Test
	void deletingARequestStillCascadesToItsDeclarations() {
		String id = jdbc.queryForObject("insert into onboarding_request default values returning id::text", String.class);
		jdbc.update("insert into income_declaration (request_id, source_code, range_code)"
				+ " values (?::uuid, 'SALARIO', 'HASTA_500')", id);
		jdbc.update("update onboarding_request set status = 'ABANDONED' where id = ?::uuid", id);

		jdbc.update("delete from onboarding_request where id = ?::uuid", id);

		assertThat(jdbc.queryForObject("select count(*) from income_declaration where request_id = ?::uuid",
				Integer.class, id)).isZero();
	}

}
