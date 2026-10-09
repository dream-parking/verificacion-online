package com.dreamparking.backend.identity.service;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.dreamparking.backend.common.exception.ApiException;
import com.dreamparking.backend.identity.entity.enums.UnreadableReason;

/**
 * The DUI photos cannot be read, so the step does not advance. Answered with 422 and the {@code reason}, which the app
 * turns into its own message (take the photo again with more light, without reflections…).
 */
public class UnreadableDocumentException extends ApiException {

	private final UnreadableReason reason;

	public UnreadableDocumentException(UnreadableReason reason) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "The identity document photos cannot be read: " + reason);
		this.reason = reason;
	}

	public UnreadableReason getReason() {
		return reason;
	}

	@Override
	public Map<String, Object> getProperties() {
		return Map.of("reason", reason.name());
	}

}
