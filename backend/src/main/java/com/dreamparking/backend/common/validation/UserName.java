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
 * Full name of a console user, {@code María-José O'Brien}: the letters of {@link PersonName} in words joined by a
 * single space, apostrophe or hyphen. No digits, other symbols, emojis or letters of other alphabets. Blanks around
 * the name are tolerated because the service trims them. {@code null} is valid; combine with {@code @NotBlank} and
 * {@code @Size}.
 */
@Documented
@Pattern(regexp = UserName.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface UserName {

	String REGEXP = PersonName.REGEXP;

	String message() default PersonName.RULE_MESSAGE;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
