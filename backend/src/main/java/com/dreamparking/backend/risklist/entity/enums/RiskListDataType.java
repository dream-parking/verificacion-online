package com.dreamparking.backend.risklist.entity.enums;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The kind of data a risk list entry holds, and how it is normalized before it is hashed. The normalization has to
 * give the same text for the value an administrator types and for the one a real request carries (the customer's
 * DUI and mobile phone as {@code customer} stores them, the device fingerprint as {@code device} stores it), or the
 * hashes would never match.
 */
public enum RiskListDataType {

	/** DUI: the nine digits, without the hyphen, so {@code 04567891-2} and {@code 045678912} are the same document. */
	DOCUMENT("Documento", RiskListDataType::normalizeDocument),
	/** Salvadoran mobile: the eight digits, without hyphen or the +503 prefix; mobiles start with 6 or 7. */
	PHONE("Teléfono", RiskListDataType::normalizePhone),
	/** Device fingerprint: stored and compared exactly as the app sends it, only trimmed. */
	DEVICE("Dispositivo", RiskListDataType::normalizeDevice);

	private static final Pattern NON_DIGITS = Pattern.compile("\\D");

	private static final int MAX_FINGERPRINT_LENGTH = 64;

	private final String label;

	private final Function<String, Optional<String>> normalizer;

	RiskListDataType(String label, Function<String, Optional<String>> normalizer) {
		this.label = label;
		this.normalizer = normalizer;
	}

	/** Name shown in the console. */
	public String label() {
		return label;
	}

	/** The value in the form that is hashed, or empty when it cannot be a value of this type (or is null or blank). */
	public Optional<String> normalize(String raw) {
		return raw == null ? Optional.empty() : normalizer.apply(raw);
	}

	private static Optional<String> normalizeDocument(String raw) {
		String digits = NON_DIGITS.matcher(raw).replaceAll("");
		return digits.length() == 9 ? Optional.of(digits) : Optional.empty();
	}

	private static Optional<String> normalizePhone(String raw) {
		String digits = NON_DIGITS.matcher(raw).replaceAll("");
		if (digits.length() == 11 && digits.startsWith("503")) {
			digits = digits.substring(3);
		}
		return digits.matches("[67][0-9]{7}") ? Optional.of(digits) : Optional.empty();
	}

	private static Optional<String> normalizeDevice(String raw) {
		String fingerprint = raw.strip();
		return fingerprint.isEmpty() || fingerprint.length() > MAX_FINGERPRINT_LENGTH ? Optional.empty()
				: Optional.of(fingerprint);
	}

}
