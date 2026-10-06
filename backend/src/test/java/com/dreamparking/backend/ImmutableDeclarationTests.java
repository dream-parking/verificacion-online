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
				"update declaracion_ingresos set rango_codigo = 'MAS_2500' where solicitud_id = ?::uuid", SUBMITTED))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("no se puede modificar ni borrar");
	}

	@Test
	void submittedIncomeDeclarationCannotBeDeleted() {
		assertThatThrownBy(
				() -> jdbc.update("delete from declaracion_ingresos where solicitud_id = ?::uuid", SUBMITTED))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void submittedExpectedActivityCannotBeEditedOrDeleted() {
		assertThatThrownBy(() -> jdbc.update(
				"update movimiento_esperado set rango_monto_codigo = 'MAS_1000' where solicitud_id = ?::uuid",
				SUBMITTED))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void declarationsOfARequestInProgressCanStillBeCorrected() {
		String id = jdbc.queryForObject("insert into solicitud default values returning id::text", String.class);
		jdbc.update("insert into declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo)"
				+ " values (?::uuid, 'SALARIO', 'HASTA_500')", id);

		int updated = jdbc.update("update declaracion_ingresos set rango_codigo = '500_1000' where solicitud_id = ?::uuid",
				id);

		assertThat(updated).isEqualTo(1);
	}

	@Test
	void deletingARequestStillCascadesToItsDeclarations() {
		String id = jdbc.queryForObject("insert into solicitud default values returning id::text", String.class);
		jdbc.update("insert into declaracion_ingresos (solicitud_id, origen_codigo, rango_codigo)"
				+ " values (?::uuid, 'SALARIO', 'HASTA_500')", id);
		jdbc.update("update solicitud set estado = 'ABANDONADA' where id = ?::uuid", id);

		jdbc.update("delete from solicitud where id = ?::uuid", id);

		assertThat(jdbc.queryForObject("select count(*) from declaracion_ingresos where solicitud_id = ?::uuid",
				Integer.class, id)).isZero();
	}

}
