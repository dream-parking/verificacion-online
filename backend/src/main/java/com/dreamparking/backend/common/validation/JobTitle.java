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
 * Job title of a console user, {@code Analista de fraude (nivel 2)}: starts with a letter and goes on with the letters
 * of {@link PersonName}, digits, spaces and {@code . , ( ) / & - '}. Numbers alone, symbols such as
 * {@code # @ % * _ + $}, question and exclamation marks, emojis and letters of other alphabets are rejected. Blanks
 * around the title are tolerated because the service trims them.
 * <p>
 * {@code null} and blank are valid (presence is {@code @NotBlank}'s job); combine with {@code @Size}.
 */
@Documented
@Pattern(regexp = JobTitle.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface JobTitle {

	String REGEXP = "\\s*|\\s*[" + PersonName.LETTERS + "][" + PersonName.LETTERS + "0-9 .,()/&'\\-]*";

	String message() default "Debe empezar con una letra; solo se permiten letras, números, espacios y . , ( ) / & - '";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
