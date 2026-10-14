package com.dreamparking.backend.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Fields that are well formed but rejected by a business rule that spans several of them (e.g. dates that do not
 * agree). Answered like a validation error: 400 with each field and its message under {@code errors}.
 */
public class InvalidFieldsException extends ApiException {

	private final Map<String, String> errors;

	public InvalidFieldsException(Map<String, String> errors) {
		super(HttpStatus.BAD_REQUEST, "Some fields are invalid: " + String.join(", ", errors.keySet()));
		this.errors = new LinkedHashMap<>(errors);
	}

	@Override
	public Map<String, Object> getProperties() {
		return Map.of("errors", new LinkedHashMap<>(errors));
	}

}
