package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BackendApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void contextLoadsAndFlywayMigrated() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success", Integer.class);
		assertThat(applied).isGreaterThanOrEqualTo(4);
	}

	@Test
	void esquemaCeibaCreado() {
		Integer tablas = jdbc.queryForObject(
				"select count(*) from information_schema.tables where table_schema = 'ceiba' and table_type = 'BASE TABLE'",
				Integer.class);
		assertThat(tablas).isEqualTo(22);
		// 2030: año sin datos semilla, así el correlativo arranca en 1.
		assertThat(jdbc.queryForObject("select ceiba.siguiente_numero_solicitud('2030-06-01T12:00:00Z')", String.class))
				.isEqualTo("SOL-2030-00001");
	}

	@Test
	void datosSemillaCargados() {
		assertThat(jdbc.queryForObject("select count(*) from ceiba.tipo_alerta", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject(
				"select nivel_riesgo::text from ceiba.solicitud where numero = 'SOL-2026-00418'", String.class))
				.isEqualTo("BAJO");
		assertThat(jdbc.queryForObject("select count(*) from ceiba.v_bandeja_alertas", Integer.class)).isEqualTo(1);
	}

}
