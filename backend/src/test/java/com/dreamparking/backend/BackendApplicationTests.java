package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev") // carga db/demo: las aserciones usan los datos de demostración
class BackendApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void contextLoadsAndFlywayMigrated() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where success", Integer.class);
		assertThat(applied).isGreaterThanOrEqualTo(5);
	}

	@Test
	void esquemaDominioEnPublic() {
		Integer tablas = jdbc.queryForObject(
				"select count(*) from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'"
						+ " and table_name <> 'flyway_schema_history'",
				Integer.class);
		assertThat(tablas).isEqualTo(23);
		assertThat(jdbc.queryForObject(
				"select count(*) from information_schema.schemata where schema_name = 'ceiba'", Integer.class)).isZero();
		// 2030: año sin datos semilla, así el correlativo arranca en 1.
		assertThat(jdbc.queryForObject("select next_request_number('2030-06-01T12:00:00Z')", String.class))
				.isEqualTo("SOL-2030-00001");
	}

	@Test
	void datosSemillaCargados() {
		assertThat(jdbc.queryForObject("select count(*) from alert_type", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject(
				"select risk_level::text from onboarding_request where number = 'SOL-2026-00418'", String.class))
				.isEqualTo("LOW");
		assertThat(jdbc.queryForObject("select count(*) from v_alert_inbox", Integer.class)).isEqualTo(9);
	}

}
