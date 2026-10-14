package com.dreamparking.backend.identity.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dreamparking.backend.common.exception.InvalidFieldsException;
import com.dreamparking.backend.identity.entity.enums.DuiDateProblem;

/**
 * Dates of a DUI as the customer confirms them. The confirmation is rejected when the DUI is expired or its dates do
 * not agree: the DUI is only issued from 18 years of age, and it cannot expire before its holder was born.
 */
public final class DuiDates {

	/** Minimum age to hold a DUI. */
	static final int MIN_AGE = 18;

	private DuiDates() {
	}

	/** What is wrong with the dates; empty when they are acceptable. */
	public static List<DuiDateProblem> problems(LocalDate birthDate, LocalDate expiryDate, LocalDate today) {
		List<DuiDateProblem> problems = new ArrayList<>();
		if (birthDate.plusYears(MIN_AGE).isAfter(today)) {
			problems.add(DuiDateProblem.UNDER_AGE);
		}
		if (!expiryDate.isAfter(birthDate)) {
			problems.add(DuiDateProblem.EXPIRY_BEFORE_BIRTH);
		}
		else if (expiryDate.isBefore(today)) {
			problems.add(DuiDateProblem.EXPIRED);
		}
		return problems;
	}

	/** The 400 answer naming each field and its message. */
	public static InvalidFieldsException rejection(List<DuiDateProblem> problems) {
		Map<String, String> errors = new LinkedHashMap<>();
		problems.forEach(p -> errors.putIfAbsent(p.field(), p.message()));
		return new InvalidFieldsException(errors);
	}

}
