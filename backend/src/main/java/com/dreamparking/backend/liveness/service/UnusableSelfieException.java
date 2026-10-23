package com.dreamparking.backend.liveness.service;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.dreamparking.backend.common.exception.ApiException;
import com.dreamparking.backend.liveness.client.SelfieProblem;

/**
 * The selfie cannot be judged because of how it was taken, so the customer has to take it again. Answered with 422 and
 * the {@code reason}, which the app turns into its own message (more light, hold still…). Nothing is saved and the
 * attempt does not count, so a bad light never weighs in the score.
 */
public class UnusableSelfieException extends ApiException {

	private final SelfieProblem reason;

	public UnusableSelfieException(SelfieProblem reason) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "The selfie cannot be judged: " + reason);
		this.reason = reason;
	}

	public SelfieProblem getReason() {
		return reason;
	}

	@Override
	public Map<String, Object> getProperties() {
		return Map.of("reason", reason.name());
	}

}
