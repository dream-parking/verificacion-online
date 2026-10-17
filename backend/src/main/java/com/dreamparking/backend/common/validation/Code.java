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
 * Code of a catalog entry, alert type, resolution or score rule: capital letters, digits, {@code _} and {@code -},
 * starting with a letter or digit ({@code PAGO_SALARIO}, {@code 500_1500}, {@code R-01}). {@code null} is valid;
 * combine with {@code @Size}.
 */
@Documented
@Pattern(regexp = Code.REGEXP)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface Code {

	String REGEXP = "[A-Z0-9][A-Z0-9_-]*";

	String message() default "Debe ser un código en mayúsculas: letras, números, _ y -";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
