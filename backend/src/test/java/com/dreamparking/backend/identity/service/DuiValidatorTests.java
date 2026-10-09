package com.dreamparking.backend.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** VDI-79: DUI check digit (weights 9 to 2) and normalization of what the OCR reads. */
class DuiValidatorTests {

	@Test
	void acceptsNumbersWhoseCheckDigitMatches() {
		// 0·9 + 3·8 + 3·7 + 7·6 + 7·5 + 8·4 + 4·3 + 1·2 = 168 → (10 - 8) % 10 = 2
		assertThat(DuiValidator.hasValidCheckDigit("03377841-2")).isTrue();
		// sum multiple of 10 → check digit 0
		assertThat(DuiValidator.hasValidCheckDigit("00000000-0")).isTrue();
	}

	@Test
	void rejectsAWrongCheckDigitOrFormat() {
		assertThat(DuiValidator.hasValidCheckDigit("03377841-3")).isFalse();
		assertThat(DuiValidator.hasValidCheckDigit("033778412")).isFalse();
		assertThat(DuiValidator.hasValidCheckDigit("0337784-12")).isFalse();
		assertThat(DuiValidator.hasValidCheckDigit(null)).isFalse();
	}

	@Test
	void normalizesTheNumberReadFromTheCard() {
		assertThat(DuiValidator.normalize("03377841-2")).isEqualTo("03377841-2");
		assertThat(DuiValidator.normalize(" 033778412 ")).isEqualTo("03377841-2");
		assertThat(DuiValidator.normalize("0337 7841 - 2")).isEqualTo("03377841-2");
		assertThat(DuiValidator.normalize("03377841")).isNull();
		assertThat(DuiValidator.normalize("O3377841-2")).isNull();
		assertThat(DuiValidator.normalize(null)).isNull();
	}

}
