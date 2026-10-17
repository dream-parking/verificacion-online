package com.dreamparking.backend.console.service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.validation.Password;

/**
 * Rules for console passwords: no emojis, 10 to 72 characters (BCrypt reads at most 72 bytes) and not the user's
 * email. Same rules as the console's form ({@code webconsole/src/lib/console/users.ts}).
 */
public final class PasswordPolicy {

	static final int MIN_LENGTH = 10;

	static final int MAX_LENGTH = 72;

	/** One emoji or emoji glue character, the same set {@link Password} rejects (© and ® are allowed). */
	private static final Pattern EMOJI = Pattern.compile("(?![©®])[" + Password.EMOJI + "]");

	private PasswordPolicy() {
	}

	public static void check(String password, String email) {
		if (password != null && EMOJI.matcher(password).find()) {
			throw new InvalidInputException(
					"La contraseña no puede tener emojis: pueden escribirse distinto en otro teclado o dispositivo");
		}
		// Characters as a person counts them (code points), not UTF-16 units: an emoji or other character outside
		// the basic plane must not count twice towards the minimum.
		if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
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
