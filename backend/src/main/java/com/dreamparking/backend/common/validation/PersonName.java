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
 * First names, last names or full name of a person, {@code María-José D'Aubuisson}: Latin letters (accents, diaeresis
 * and ñ included) in words joined by a single space, apostrophe or hyphen (VDI-88, the same rule as the app, VDI-89);
 * no digits, other symbols, emojis or letters of other alphabets, and no apostrophe or hyphen at the start, at the end
 * or repeated. Blanks around the name are tolerated because the services trim them. {@code null} is valid; combine
 * with {@code @NotBlank} and {@code @Size}.
 * <p>
 * Checked on the way in, not on the entities: a rule on an entity would also reject saving a record already stored
 * with other data.
 */
@Documented
@Pattern(regexp = PersonName.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface PersonName {

	/**
	 * Latin letters with accents, diaeresis and ñ: basic Latin, Latin-1 and Latin Extended-A, without {@code ª º × ÷}.
	 * Written as ranges instead of {@code \p{IsLatin}} so the pattern published in the OpenAPI also works in
	 * JavaScript.
	 */
	String LETTERS = "A-Za-zÀ-ÖØ-öø-ž";

	String REGEXP = "\\s*[" + LETTERS + "]+([ '\\-][" + LETTERS + "]+)*\\s*";

	String MESSAGE = "Solo se permiten letras (incluidas tildes y ñ), espacios, apóstrofo (') y guion (-)";

	String message() default MESSAGE;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
