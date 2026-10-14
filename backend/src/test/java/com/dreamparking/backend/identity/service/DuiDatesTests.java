package com.dreamparking.backend.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.identity.entity.enums.DuiDateProblem;

/** An expired DUI or dates that do not agree are rejected when the customer confirms them. */
class DuiDatesTests {

	static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

	@Test
	void acceptsAnAdultWithAValidDui() {
		assertThat(DuiDates.problems(LocalDate.of(1991, 3, 14), LocalDate.of(2031, 7, 22), TODAY)).isEmpty();
		// Expires today: still valid. Turns 18 today: old enough.
		assertThat(DuiDates.problems(LocalDate.of(2008, 10, 14), TODAY, TODAY)).isEmpty();
	}

	@Test
	void rejectsAnExpiredDui() {
		assertThat(DuiDates.problems(LocalDate.of(1991, 3, 14), LocalDate.of(2026, 10, 13), TODAY))
			.containsExactly(DuiDateProblem.EXPIRED);
	}

	@Test
	void rejectsABirthDateOfSomeoneUnder18() {
		assertThat(DuiDates.problems(LocalDate.of(2008, 10, 15), LocalDate.of(2031, 7, 22), TODAY))
			.containsExactly(DuiDateProblem.UNDER_AGE);
	}

	@Test
	void rejectsAnExpiryDateThatIsNotAfterTheBirthDate() {
		assertThat(DuiDates.problems(LocalDate.of(1991, 3, 14), LocalDate.of(1991, 3, 14), TODAY))
			.containsExactly(DuiDateProblem.EXPIRY_BEFORE_BIRTH);
	}

	@Test
	void theRejectionNamesEveryFieldThatFails() {
		List<DuiDateProblem> problems = DuiDates.problems(LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1), TODAY);

		assertThat(DuiDates.rejection(problems).getProperties()).isEqualTo(Map.of("errors",
				Map.of("birthDate", DuiDateProblem.UNDER_AGE.message(), "expiryDate",
						DuiDateProblem.EXPIRY_BEFORE_BIRTH.message())));
	}

}
