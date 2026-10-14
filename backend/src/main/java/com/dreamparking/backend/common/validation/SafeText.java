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
 * Values a program generates rather than a person (device model, app version, fingerprints, tokens), whose exact
 * shape we do not control: any printable character except {@code < >}, emojis and other symbols, control
 * characters (the NUL byte breaks PostgreSQL), invisible formatting characters and unassigned code points.
 * <p>
 * {@code null} is valid; combine with {@code @Size}. Prefer a stricter format when there is one.
 */
@Documented
@Pattern(regexp = SafeText.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface SafeText {

	String REGEXP = "[^\\p{C}\\p{So}\\p{Sk}\\p{Zl}\\p{Zp}<>]*";

	String message() default "Tiene caracteres no permitidos";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
