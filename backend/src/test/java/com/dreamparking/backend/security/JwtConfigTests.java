package com.dreamparking.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtConfigTests {

	private final JwtConfig config = new JwtConfig();

	@Test
	void usesTheConfiguredSecret() {
		assertThat(config.jwtSecretKey("0123456789abcdef0123456789abcdef").getEncoded()).hasSize(32);
	}

	@Test
	void fallsBackToARandomKeyWhenNoSecretIsSet() {
		var first = config.jwtSecretKey("").getEncoded();
		var second = config.jwtSecretKey("  ").getEncoded();
		assertThat(first).hasSize(32).isNotEqualTo(second);
	}

	@Test
	void refusesAShortSecret() {
		assertThatThrownBy(() -> config.jwtSecretKey("demasiado-corto")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 32 bytes");
	}

}
