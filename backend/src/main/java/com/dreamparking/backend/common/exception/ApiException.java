package com.dreamparking.backend.common.exception;

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

}
