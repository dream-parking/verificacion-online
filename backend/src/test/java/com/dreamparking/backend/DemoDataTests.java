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
		assertThat(jdbc.queryForObject("select count(*) from solicitud", Integer.class)).isEqualTo(10);
		assertThat(jdbc.queryForObject("select count(*) from solicitud where estado = 'COMPLETADA'", Integer.class))
			.isEqualTo(8);
		assertThat(jdbc.queryForObject(
				"select count(*) from solicitud where estado = 'EN_PROGRESO' and numero is null", Integer.class))
			.isEqualTo(2);
	}

	@Test
	void riskScoresFollowRuleR01() {
		Map<String, Integer> byLevel = new java.util.HashMap<>();
		jdbc.queryForList("select nivel_riesgo::text as n, count(*) as c from solicitud group by nivel_riesgo")
			.forEach(row -> byLevel.put((String) row.get("n"), ((Number) row.get("c")).intValue()));

		// low: 418 (200_500), 415 (200_500), 412 (HASTA_200), 406 (HASTA_200); pending: 417, 410, 409, 401; none: 2 unfinished
		assertThat(byLevel).containsEntry("BAJO", 4).containsEntry("PENDIENTE_EVALUACION", 4).containsEntry("SIN_EVALUAR", 2);
		assertThat(jdbc.queryForObject("select count(*) from evaluacion_riesgo where es_vigente", Integer.class))
			.isEqualTo(8);
	}

	@Test
	void everySubmittedRequestHasItsFullTrail() {
		List<String> ids = jdbc.queryForList("select id::text from solicitud where estado = 'COMPLETADA'", String.class);
		assertThat(ids).hasSize(8);
		for (String id : ids) {
			assertThat(jdbc.queryForObject("select count(*) from paso_solicitud where solicitud_id = ?::uuid and completado_en is not null",
					Integer.class, id)).as("steps of " + id).isEqualTo(5);
			assertThat(jdbc.queryForObject("select count(*) from evento_solicitud where solicitud_id = ?::uuid", Integer.class, id))
				.as("events of " + id)
				.isGreaterThanOrEqualTo(6);
			assertThat(jdbc.queryForObject("select count(*) from sesion_onboarding where solicitud_id = ?::uuid", Integer.class, id))
				.as("session of " + id)
				.isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from declaracion_ingresos where solicitud_id = ?::uuid", Integer.class, id))
				.isEqualTo(1);
			assertThat(jdbc.queryForObject("select count(*) from movimiento_esperado where solicitud_id = ?::uuid", Integer.class, id))
				.isEqualTo(1);
		}
	}

	@Test
	void timelineIsInOrderAndTheScoreComesAfterTheSubmission() {
		List<String> types = jdbc.queryForList(
				"select tipo::text from evento_solicitud where solicitud_id = '00000000-0000-0000-0000-000000000418'::uuid order by ocurrido_en, id",
				String.class);
		assertThat(types).containsExactly("SOLICITUD_INICIADA", "PRIVACIDAD_ACEPTADA", "DATOS_BASICOS_COMPLETADOS",
				"INGRESOS_REGISTRADOS", "MOVIMIENTO_REGISTRADO", "SOLICITUD_ENVIADA", "SCORE_ASIGNADO");
	}

	@Test
	void unfinishedRequestsStopWhereTheyLeftOff() {
		assertThat(jdbc.queryForObject("select etapas_completadas from solicitud where id = '00000000-0000-0000-0000-000000000414'::uuid", Integer.class))
			.isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from movimiento_esperado where solicitud_id = '00000000-0000-0000-0000-000000000414'::uuid", Integer.class))
			.isZero();
		assertThat(jdbc.queryForObject("select etapas_completadas from solicitud where id = '00000000-0000-0000-0000-000000000408'::uuid", Integer.class))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from declaracion_ingresos where solicitud_id = '00000000-0000-0000-0000-000000000408'::uuid", Integer.class))
			.isZero();
	}

}
