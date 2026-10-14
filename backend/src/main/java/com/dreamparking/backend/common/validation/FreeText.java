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
 * Text a person writes on one line (labels, job titles, reasons): letters, digits, currency signs, spaces and common
 * punctuation, including the typographic quotes and dashes that phone keyboards put on their own. It has to contain
 * at least one letter or digit. Emojis, other symbols, {@code < > { } [ ] \ | ^ ~ `}, control and invisible
 * characters are rejected. For comments with line breaks use {@link MultilineText}.
 * <p>
 * {@code null} and blank are valid (presence is {@code @NotBlank}'s job); combine with {@code @Size}.
 */
@Documented
@Pattern(regexp = FreeText.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface FreeText {

	/** Characters allowed besides letters and digits. */
	String PUNCTUATION = "\\p{Sc} .,;:¿?¡!'\"‘’“”«»()/&%#@+*_°–—…\\-";

	String REGEXP = "\\s*|[\\p{L}\\p{N}" + PUNCTUATION + "]*[\\p{L}\\p{N}][\\p{L}\\p{N}" + PUNCTUATION + "]*";

	String message() default "Solo se permiten letras, números, espacios y signos de puntuación comunes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
