package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The demo requests (V4, V9) mirror the console's sample data and score as the console expects. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class DemoDataTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void tenRequestsEightSubmittedAndTwoInProgress() {
		assertThat(jdbc.queryForObject("select count(*) from onboarding_request", Integer.class)).isEqualTo(10);
		assertThat(jdbc.queryForObject("select count(*) from onboarding_request where status = 'COMPLETED'", Integer.class))
			.isEqualTo(8);
		assertThat(jdbc.queryForObject(
				"select count(*) from onboarding_request where status = 'IN_PROGRESS' and number is null", Integer.class))
			.isEqualTo(2);
	}

	@Test
	void riskScoresFollowRuleR01() {
		Map<String, Integer> byLevel = new java.util.HashMap<>();
		jdbc.queryForList("select risk_level::text as n, count(*) as c from onboarding_request group by risk_level")
			.forEach(row -> byLevel.put((String) row.get("n"), ((Number) row.get("c")).intValue()));

		// low: 418 (200_500), 415 (200_500), 412 (HASTA_200), 406 (HASTA_200); pending: 417, 410, 409, 401; none: 2 unfinished
		assertThat(byLevel).containsEntry("LOW", 4).containsEntry("PENDING_REVIEW", 4).containsEntry("NOT_EVALUATED", 2);
		assertThat(jdbc.queryForObject("select count(*) from risk_assessment where is_current", Integer.class))
			.isEqualTo(8);
	}

	@Test
	void everySubmittedRequestHasItsFullTrail() {
		List<String> ids = jdbc.queryForList("select id::text from onboarding_request where status = 'COMPLETED'", String.class);
		assertThat(ids).hasSize(8);
		for (String id : ids) {
			assertThat(jdbc.queryForObject("select count(*) from request_step where request_id = ?::uuid and completed_at is not null",
					Integer.class, id)).as("steps of " + id).isEqualTo(5);
			assertThat(jdbc.queryForObject("select count(*) from request_event where request_id = ?::uuid", Integer.class, id))
				.as("events of " + id)
				.isGreaterThanOrEqualTo(6);
			assertThat(jdbc.queryForObject("select count(*) from onboarding_session where request_id = ?::uuid", Integer.class, id))
				.as("session of " + id)
				.isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from income_declaration where request_id = ?::uuid", Integer.class, id))
				.isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from expected_activity where request_id = ?::uuid", Integer.class, id))
				.isEqualTo(1);
		}
	}

	@Test
	void timelineIsInOrderAndTheScoreComesAfterTheSubmission() {
		List<String> types = jdbc.queryForList(
				"select type::text from request_event where request_id = '00000000-0000-0000-0000-000000000418'::uuid order by occurred_at, id",
				String.class);
		assertThat(types).containsExactly("REQUEST_STARTED", "PRIVACY_ACCEPTED", "BASIC_DATA_COMPLETED",
				"INCOME_REGISTERED", "EXPECTED_ACTIVITY_REGISTERED", "REQUEST_SUBMITTED", "SCORE_ASSIGNED");
	}

	@Test
	void unfinishedRequestsStopWhereTheyLeftOff() {
		assertThat(jdbc.queryForObject("select completed_steps from onboarding_request where id = '00000000-0000-0000-0000-000000000414'::uuid", Integer.class))
			.isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from expected_activity where request_id = '00000000-0000-0000-0000-000000000414'::uuid", Integer.class))
			.isZero();
		assertThat(jdbc.queryForObject("select completed_steps from onboarding_request where id = '00000000-0000-0000-0000-000000000408'::uuid", Integer.class))
			.isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from income_declaration where request_id = '00000000-0000-0000-0000-000000000408'::uuid", Integer.class))
			.isZero();
	}

}
