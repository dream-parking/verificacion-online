package com.dreamparking.backend.common;

/** Too many failed sign-ins for an email. Answered with 429 and a {@code Retry-After} header. */
public class TooManyAttemptsException extends RuntimeException {

	private final long retryAfterSeconds;

	public TooManyAttemptsException(long retryAfterSeconds) {
		super("Demasiados intentos fallidos. Intenta de nuevo en unos minutos.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
