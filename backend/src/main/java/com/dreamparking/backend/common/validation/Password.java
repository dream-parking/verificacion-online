package com.dreamparking.backend.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/**
 * A password, as typed: any character except control characters and emojis, on every endpoint (sign-in included).
 * The rest of the rules for a new password (length, not the email…) are in {@code PasswordPolicy}.
 */
@Documented
@Pattern(regexp = Password.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface Password {

	/**
	 * Emojis, flags, skin tones and the characters that glue them together (zero-width joiner, variation selectors,
	 * keycap, tags), as the inside of a character class. © and ® are emoji too, but they are ordinary symbols on a
	 * keyboard and are allowed.
	 */
	String EMOJI = "\\p{IsExtended_Pictographic}\\p{IsEmoji_Modifier}\\x{1F1E6}-\\x{1F1FF}\\u200D\\uFE0E\\uFE0F\\u20E3"
			+ "\\x{E0020}-\\x{E007F}";

	String REGEXP = "(?:[©®]|[^\\p{Cc}" + EMOJI + "])*";

	String message() default "La contraseña no puede tener emojis ni caracteres de control";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
