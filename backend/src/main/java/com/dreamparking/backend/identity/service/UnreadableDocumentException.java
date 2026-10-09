package com.dreamparking.backend.identity.service;

import java.util.Map;

import org.springframework.http.HttpStatus;

import com.dreamparking.backend.common.exception.ApiException;
import com.dreamparking.backend.identity.entity.enums.UnreadableReason;
import com.dreamparking.backend.identity.entity.enums.UnreadableSide;

/**
 * The DUI photos cannot be read, so the step does not advance. Answered with 422, the {@code reason}, which the app
 * turns into its own message (take the photo again with more light, without reflections…), and the {@code side} to
 * take again ({@code FRONT}, {@code BACK} or {@code BOTH}; absent when unknown).
 */
public class UnreadableDocumentException extends ApiException {

	private final UnreadableReason reason;

	private final UnreadableSide side;

	public UnreadableDocumentException(UnreadableReason reason, UnreadableSide side) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "The identity document photos cannot be read: " + reason);
		this.reason = reason;
		this.side = side;
	}

	public UnreadableReason getReason() {
		return reason;
	}

	public UnreadableSide getSide() {
		return side;
	}

	@Override
	public Map<String, Object> getProperties() {
		return side == null ? Map.of("reason", reason.name()) : Map.of("reason", reason.name(), "side", side.name());
	}

}
