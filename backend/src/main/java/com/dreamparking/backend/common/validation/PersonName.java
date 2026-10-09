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
 * First names, last names or full name of a person: letters (accents and ñ included) in words separated by single
 * spaces; no digits, symbols or emojis. Blanks around the name are tolerated because the services trim them.
 * {@code null} is valid; combine with {@code @NotBlank} and {@code @Size}.
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

	String REGEXP = "\\s*\\p{L}+( \\p{L}+)*\\s*";

	String message() default "Solo se permiten letras y espacios";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
