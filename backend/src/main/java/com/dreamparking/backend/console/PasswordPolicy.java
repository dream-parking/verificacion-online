package com.dreamparking.backend.console;

import java.nio.charset.StandardCharsets;

import com.dreamparking.backend.common.InvalidInputException;

/** Rules for console passwords: 10 to 72 characters (BCrypt reads at most 72 bytes) and not the user's email. */
final class PasswordPolicy {

	static final int MIN_LENGTH = 10;

	static final int MAX_LENGTH = 72;

	private PasswordPolicy() {
	}

	static void check(String password, String email) {
		if (password == null || password.length() < MIN_LENGTH) {
			throw new InvalidInputException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
			throw new InvalidInputException("La contraseña no puede pasar de " + MAX_LENGTH + " bytes");
		}
		if (password.equalsIgnoreCase(email)) {
			throw new InvalidInputException("La contraseña no puede ser igual al correo");
		}
	}

}
