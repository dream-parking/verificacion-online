package com.dreamparking.backend.identity.service;

import java.util.regex.Pattern;

/**
 * Format and check digit of a DUI number ({@code 00000000-0}). The check digit is computed from the first eight
 * digits with weights 9 to 2: {@code (10 - sum % 10) % 10}. An invalid check digit is a risk signal, not a reason to
 * reject the request on its own.
 */
public final class DuiValidator {

	private static final Pattern FORMAT = Pattern.compile("\\d{8}-\\d");

	private DuiValidator() {
	}

	/** True when the number has the {@code 00000000-0} format and its check digit matches. */
	public static boolean hasValidCheckDigit(String dui) {
		if (dui == null || !FORMAT.matcher(dui).matches()) {
			return false;
		}
		int sum = 0;
		for (int i = 0; i < 8; i++) {
			sum += (dui.charAt(i) - '0') * (9 - i);
		}
		return (10 - sum % 10) % 10 == dui.charAt(9) - '0';
	}

	/**
	 * The number in the {@code 00000000-0} format, from text that may have spaces or no hyphen; {@code null} when it
	 * does not have exactly nine digits.
	 */
	public static String normalize(String text) {
		if (text == null) {
			return null;
		}
		String digits = text.replaceAll("[\\s-]", "");
		if (!digits.matches("\\d{9}")) {
			return null;
		}
		return digits.substring(0, 8) + "-" + digits.charAt(8);
	}

}
