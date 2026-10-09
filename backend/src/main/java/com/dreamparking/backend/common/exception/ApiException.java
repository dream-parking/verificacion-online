package com.dreamparking.backend.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Business error whose message is safe to show to the client. {@link ApiExceptionHandler} turns it into a
 * Problem Details response with this status and the message as {@code detail}.
 */
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;

	protected ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

	/** Extra members of the Problem Details body, for errors the client handles by code (none by default). */
	public Map<String, Object> getProperties() {
		return Map.of();
	}

}
