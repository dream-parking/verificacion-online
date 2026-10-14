package com.dreamparking.backend.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.common.exception.InvalidFieldsException;

/** An expired DUI or dates that do not agree are rejected when the customer confirms them. */
class DuiDatesTests {

	static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

	@Test
	void acceptsAnAdultWithAValidDui() {
		assertThatCode(() -> DuiDates.check(LocalDate.of(1991, 3, 14), LocalDate.of(2031, 7, 22), TODAY))
			.doesNotThrowAnyException();
		// Expires today: still valid. Turns 18 today: old enough.
		assertThatCode(() -> DuiDates.check(LocalDate.of(2008, 10, 14), TODAY, TODAY)).doesNotThrowAnyException();
	}

	@Test
	void rejectsAnExpiredDui() {
		assertThat(errors(LocalDate.of(1991, 3, 14), LocalDate.of(2026, 10, 13)))
			.isEqualTo(Map.of("expiryDate", DuiDates.EXPIRED));
	}

	@Test
	void rejectsABirthDateOfSomeoneUnder18() {
		assertThat(errors(LocalDate.of(2008, 10, 15), LocalDate.of(2031, 7, 22)))
			.isEqualTo(Map.of("birthDate", DuiDates.UNDER_AGE));
	}

	@Test
	void rejectsAnExpiryDateThatIsNotAfterTheBirthDate() {
		assertThat(errors(LocalDate.of(1991, 3, 14), LocalDate.of(1991, 3, 14)))
			.isEqualTo(Map.of("expiryDate", DuiDates.EXPIRY_BEFORE_BIRTH));
	}

	@Test
	void namesEveryFieldThatFails() {
		assertThat(errors(LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1))).containsOnlyKeys("birthDate",
				"expiryDate");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String> errors(LocalDate birthDate, LocalDate expiryDate) {
		Throwable ex = catchThrowable(() -> DuiDates.check(birthDate, expiryDate, TODAY));
		assertThat(ex).isInstanceOf(InvalidFieldsException.class);
		return (Map<String, String>) ((InvalidFieldsException) ex).getProperties().get("errors");
	}

}
