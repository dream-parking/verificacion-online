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
 * Like {@link FreeText}, with line breaks: comments for the alert history and similar. {@code null} and blank are
 * valid; combine with {@code @Size}.
 */
@Documented
@Pattern(regexp = MultilineText.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface MultilineText {

	String REGEXP = "\\s*|[\\p{L}\\p{N}\\r\\n" + FreeText.PUNCTUATION + "]*[\\p{L}\\p{N}][\\p{L}\\p{N}\\r\\n"
			+ FreeText.PUNCTUATION + "]*";

	String message() default "Solo se permiten letras, números, espacios, saltos de línea y signos de puntuación comunes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
