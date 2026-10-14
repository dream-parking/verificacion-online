package com.dreamparking.backend.identity.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.dreamparking.backend.common.exception.InvalidFieldsException;

/**
 * Dates of a DUI as the customer confirms them. The request is rejected when the DUI is expired or its dates do not
 * agree: the DUI is only issued from 18 years of age, and it cannot expire before its holder was born.
 */
public final class DuiDates {

	/** Minimum age to hold a DUI. */
	static final int MIN_AGE = 18;

	static final String EXPIRED = "El DUI está vencido";

	static final String EXPIRY_BEFORE_BIRTH = "La fecha de vencimiento debe ser posterior a la de nacimiento";

	static final String UNDER_AGE = "La fecha de nacimiento no concuerda con un DUI: se emite a partir de los 18 años";

	private DuiDates() {
	}

	/**
	 * @throws InvalidFieldsException naming {@code birthDate} and {@code expiryDate} when they are not acceptable
	 */
	public static void check(LocalDate birthDate, LocalDate expiryDate, LocalDate today) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (birthDate.plusYears(MIN_AGE).isAfter(today)) {
			errors.put("birthDate", UNDER_AGE);
		}
		if (!expiryDate.isAfter(birthDate)) {
			errors.put("expiryDate", EXPIRY_BEFORE_BIRTH);
		}
		else if (expiryDate.isBefore(today)) {
			errors.put("expiryDate", EXPIRED);
		}
		if (!errors.isEmpty()) {
			throw new InvalidFieldsException(errors);
		}
	}

}
