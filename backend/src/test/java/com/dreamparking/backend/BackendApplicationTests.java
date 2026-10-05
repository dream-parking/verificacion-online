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
		assertThat(applied).isGreaterThanOrEqualTo(1);
	}

}
