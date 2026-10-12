package com.dreamparking.backend.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every API error is answered as Problem Details (RFC 9457), with a {@code detail} the client can show:
 * <pre>
 * {"status": 409, "title": "Conflict", "detail": "Complete the 4 previous steps before submitting", ...}
 * </pre>
 * Validation errors also list each invalid field under {@code errors}. Spring's own MVC errors (wrong path
 * variable type, unsupported method…) are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ProblemDetail businessError(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		ex.getProperties().forEach(problem::setProperty);
		return problem;
	}

	/** Same as any business error, plus the {@code Retry-After} header. */
	@ExceptionHandler(TooManyAttemptsException.class)
	public ResponseEntity<ProblemDetail> tooManyAttempts(TooManyAttemptsException ex) {
		return ResponseEntity.status(ex.getStatus())
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
			.body(ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage()));
	}

	/** Someone else changed the same record first (optimistic locking): the client should reload and retry. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ProblemDetail concurrentModification() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The record was modified by someone else. Reload it and try again.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return invalidFields(errors);
	}

	/**
	 * Raised instead of the one above when the method also has constraints on its query parameters or path
	 * variables: same answer, with each parameter listed by its name next to the body fields.
	 */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors body) {
				for (FieldError error : body.getFieldErrors()) {
					errors.putIfAbsent(error.getField(), error.getDefaultMessage());
				}
			}
			else {
				String name = result.getMethodParameter().getParameterName();
				result.getResolvableErrors().forEach(error -> errors.putIfAbsent(name, error.getDefaultMessage()));
			}
		}
		return invalidFields(errors);
	}

	private static ResponseEntity<Object> invalidFields(Map<String, String> errors) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Some fields are invalid: " + String.join(", ", errors.keySet()));
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	/** Malformed JSON or a value of the wrong type (e.g. an unknown enum value); the parser message is not exposed. */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest()
			.body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
					"The request body is not valid JSON or a field has a value of the wrong type."));
	}

}
