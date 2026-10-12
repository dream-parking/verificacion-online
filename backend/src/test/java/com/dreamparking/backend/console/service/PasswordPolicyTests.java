package com.dreamparking.backend.console.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.dreamparking.backend.common.exception.InvalidInputException;

/** The console password rules, which the console's form repeats in {@code users.ts}. */
class PasswordPolicyTests {

	private static final String EMAIL = "ana@ceiba.example";

	@Test
	void acceptsAnOrdinaryPasswordAndTheSymbolsCopyrightAndRegistered() {
		assertThatCode(() -> PasswordPolicy.check("clave-larga-1", EMAIL)).doesNotThrowAnyException();
		assertThatCode(() -> PasswordPolicy.check("clave©larga®1", EMAIL)).doesNotThrowAnyException();
	}

	@Test
	void rejectsAMissingOrShortPassword() {
		assertThatThrownBy(() -> PasswordPolicy.check(null, EMAIL)).isInstanceOf(InvalidInputException.class)
				.hasMessageContaining("al menos");
		assertThatThrownBy(() -> PasswordPolicy.check("corta", EMAIL)).isInstanceOf(InvalidInputException.class)
				.hasMessageContaining("al menos");
	}

	@Test
	void rejectsMoreThan72BytesNotCharacters() {
		assertThatCode(() -> PasswordPolicy.check("a".repeat(72), EMAIL)).doesNotThrowAnyException();
		assertThatThrownBy(() -> PasswordPolicy.check("a".repeat(73), EMAIL)).isInstanceOf(InvalidInputException.class)
				.hasMessageContaining("72 bytes");
		assertThatThrownBy(() -> PasswordPolicy.check("é".repeat(37), EMAIL)).isInstanceOf(InvalidInputException.class)
				.hasMessageContaining("72 bytes");
	}

	@ParameterizedTest
	@ValueSource(strings = { "clave-larga-😀", "clave-larga-👍🏽", "clave-larga-🇸🇻", "clave-larga-‍",
			"clave-larga-️", "clave-larga-1⃣" })
	void rejectsEmojisAndTheirGlueCharacters(String password) {
		assertThatThrownBy(() -> PasswordPolicy.check(password, EMAIL)).isInstanceOf(InvalidInputException.class)
				.hasMessageContaining("emojis");
	}

	@Test
	void rejectsThePasswordWhenItIsTheEmail() {
		assertThatThrownBy(() -> PasswordPolicy.check("ANA@ceiba.example", EMAIL)).isInstanceOf(InvalidInputException.class)
				.hasMessageContaining("igual al correo");
	}

}
