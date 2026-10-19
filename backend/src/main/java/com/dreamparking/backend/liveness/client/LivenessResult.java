package com.dreamparking.backend.liveness.client;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a provider answers about a selfie. {@code confidence} goes from 0 (surely not a live person) to 1 (surely a
 * live person), with two decimals; it is never a yes or no.
 */
public record LivenessResult(BigDecimal confidence) {

	public LivenessResult {
		if (confidence == null) {
			throw new IllegalArgumentException("confidence is required");
		}
		confidence = confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
	}

}
