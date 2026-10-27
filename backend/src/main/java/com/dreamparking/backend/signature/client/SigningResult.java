package com.dreamparking.backend.signature.client;

/** What the signature service answered: signed, or rejected with a reason. */
public record SigningResult(boolean signed, String rejectionReason) {

	public static SigningResult accepted() {
		return new SigningResult(true, null);
	}

	public static SigningResult rejected(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A rejected signature must say why");
		}
		return new SigningResult(false, reason);
	}

}
