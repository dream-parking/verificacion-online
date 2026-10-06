package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Too many failed sign-ins for an email. Answered with 429 and a {@code Retry-After} header. */
public class TooManyAttemptsException extends ApiException {

	private final long retryAfterSeconds;

	public TooManyAttemptsException(long retryAfterSeconds) {
		super(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos fallidos. Intenta de nuevo en unos minutos.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
