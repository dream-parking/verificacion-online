package com.dreamparking.backend.console.service;

import java.nio.charset.StandardCharsets;

import com.dreamparking.backend.common.exception.InvalidInputException;

/**
 * Rules for console passwords: 10 to 72 characters (BCrypt reads at most 72 bytes), no emojis and not the user's
 * email. Same rules as the console's form ({@code webconsole/src/lib/console/users.ts}).
 */
public final class PasswordPolicy {

	static final int MIN_LENGTH = 10;

	static final int MAX_LENGTH = 72;

	private PasswordPolicy() {
	}

	public static void check(String password, String email) {
		if (password == null || password.length() < MIN_LENGTH) {
			throw new InvalidInputException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
			throw new InvalidInputException("La contraseña no puede pasar de " + MAX_LENGTH + " bytes");
		}
		if (password.codePoints().anyMatch(PasswordPolicy::isEmoji)) {
			throw new InvalidInputException(
					"La contraseña no puede tener emojis: pueden escribirse distinto en otro teclado o dispositivo");
		}
		if (password.equalsIgnoreCase(email)) {
			throw new InvalidInputException("La contraseña no puede ser igual al correo");
		}
	}

	/**
	 * Emojis, flags, skin tones and the characters that glue them together (zero-width joiner, variation selector,
	 * keycap). © and ® are emoji too, but they are ordinary symbols on a keyboard and are allowed.
	 */
	private static boolean isEmoji(int codePoint) {
		if (codePoint == '©' || codePoint == '®') {
			return false;
		}
		return Character.isExtendedPictographic(codePoint) || Character.isEmojiModifier(codePoint)
				|| (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) || codePoint == 0x200D || codePoint == 0xFE0F
				|| codePoint == 0x20E3;
	}

}
