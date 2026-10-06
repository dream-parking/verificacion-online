package com.dreamparking.backend.console.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsoleUserServiceTests {

	@Test
	void initialsAreTheFirstLettersOfTheFirstTwoWords() {
		assertThat(ConsoleUserService.initialsOf("Ana Beltrán")).isEqualTo("AB");
		assertThat(ConsoleUserService.initialsOf("  luis   alberto  barahona ")).isEqualTo("LA");
		assertThat(ConsoleUserService.initialsOf("Karen")).isEqualTo("K");
	}

}
