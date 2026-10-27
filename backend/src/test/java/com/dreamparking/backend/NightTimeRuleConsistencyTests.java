package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;
import com.dreamparking.backend.risk.service.ScoreEngine;
import com.dreamparking.backend.risk.service.ScoreEngine.Inputs;

/**
 * VDI-129 (ADR 0005): the night-time rule lives twice, in the {@code night_time} column of {@code v_request_signals}
 * and in what {@link ScoreEngine} tells the console about it. This fails if one of them changes and the other does not:
 * the hours {@code ScoreEngine} announces are compared with what the view answers for every hour of the day, on both
 * sides of each edge.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class NightTimeRuleConsistencyTests {

	private static final ZoneId EL_SALVADOR = ZoneId.of("America/El_Salvador");

	/** "entre las 9 p. m. y las 5 a. m.": the two hours of ScoreEngine's observation, as 12-hour clock times. */
	private static final Pattern HOURS = Pattern.compile("(\\d{1,2}) ([ap])\\. m\\.");

	@Autowired
	JdbcTemplate jdbc;

	String customerId;

	@BeforeEach
	void customer() {
		customerId = jdbc.queryForObject("""
				insert into customer (dui, first_names, last_names, mobile_phone)
				values ('09123460-1', 'Ana', 'Pérez', '7123-4160') returning id::text""", String.class);
	}

	@Test
	void scoreEngineAnnouncesTheNightWindowTheViewUses() {
		int[] window = nightWindowAnnouncedByScoreEngine();

		for (int hour = 0; hour < 24; hour++) {
			for (int minute : new int[] { 0, 59 }) {
				assertThat(viewSaysNight(hour, minute))
					.as("v_request_signals at %02d:%02d, with ScoreEngine announcing from %d:00 to %d:00", hour, minute,
							window[0], window[1])
					.isEqualTo(inWindow(hour, window[0], window[1]));
			}
		}
	}

	@Test
	void theEdgesAreTheOnesTheEngineAnnounces() {
		int[] window = nightWindowAnnouncedByScoreEngine();

		assertThat(window).containsExactly(21, 5);
		assertThat(viewSaysNight(20, 59)).isFalse();
		assertThat(viewSaysNight(21, 0)).isTrue();
		assertThat(viewSaysNight(4, 59)).isTrue();
		assertThat(viewSaysNight(5, 0)).isFalse();
	}

	/** The start and the end (24-hour clock) of the night, read from the text the engine gives for a night request. */
	private static int[] nightWindowAnnouncedByScoreEngine() {
		RequestSignals signals = mock(RequestSignals.class);
		when(signals.getNightTime()).thenReturn(true);
		String observation = ScoreEngine.score(modelWithTimeOfDay(), new Inputs(null, null, signals, null, null, null))
			.contributions()
			.stream()
			.filter(c -> c.signal() == ScoreSignal.TIME_OF_DAY)
			.findFirst()
			.orElseThrow()
			.observation();

		Matcher matcher = HOURS.matcher(observation);
		int[] window = new int[2];
		for (int i = 0; i < window.length; i++) {
			assertThat(matcher.find()).as("hour %d in \"%s\"", i + 1, observation).isTrue();
			window[i] = to24Hours(Integer.parseInt(matcher.group(1)), matcher.group(2).equals("p"));
		}
		return window;
	}

	private static int to24Hours(int hour12, boolean afternoon) {
		return hour12 % 12 + (afternoon ? 12 : 0);
	}

	private static boolean inWindow(int hour, int start, int end) {
		return start <= end ? hour >= start && hour < end : hour >= start || hour < end;
	}

	/** Whether the view calls a request started and sent at this local time night-time. */
	private boolean viewSaysNight(int hour, int minute) {
		var instant = LocalDateTime.of(2026, 10, 20, hour, minute).atZone(EL_SALVADOR).toOffsetDateTime();
		String requestId = jdbc.queryForObject("""
				insert into onboarding_request (customer_id, dui, first_names, status, completed_steps, number, started_at,
				                                submitted_at)
				values (?::uuid, '09123460-1', 'Ana', 'COMPLETED', 6, next_request_number(), ?, ?) returning id::text""",
				String.class, customerId, instant, instant);
		return Boolean.TRUE.equals(jdbc.queryForObject("select night_time from v_request_signals where request_id = ?::uuid",
				Boolean.class, requestId));
	}

	private static ScoreModel modelWithTimeOfDay() {
		ScoreModel model = new ScoreModel();
		Map<ScoreSignal, Short> weights = new EnumMap<>(ScoreSignal.class);
		weights.put(ScoreSignal.TIME_OF_DAY, (short) 10);
		ReflectionTestUtils.setField(model, "version", (short) 1);
		ReflectionTestUtils.setField(model, "weights", weights);
		ReflectionTestUtils.setField(model, "lowMax", (short) 39);
		ReflectionTestUtils.setField(model, "mediumMax", (short) 69);
		return model;
	}

}
