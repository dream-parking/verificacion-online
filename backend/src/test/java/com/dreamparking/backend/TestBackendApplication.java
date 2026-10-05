package com.dreamparking.backend;

import org.springframework.boot.SpringApplication;

/** Run locally with a throwaway Postgres: `./mvnw spring-boot:test-run`. */
public class TestBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
