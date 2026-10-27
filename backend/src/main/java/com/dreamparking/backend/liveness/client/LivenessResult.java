package com.dreamparking.backend.liveness.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * What a provider answers about a selfie: either the {@code confidence} that it shows a live person, from 0 (surely
 * not) to 1 (surely), with two decimals and never a yes or no; or a {@code problem} with how it was taken (too dark,
 * blurry…) that makes it impossible to judge, with no confidence.
 */
public record LivenessResult(BigDecimal confidence, SelfieProblem problem) {

	public LivenessResult {
		if (problem != null) {
			confidence = null;
		}
		else if (confidence == null) {
			throw new IllegalArgumentException("confidence is required when the selfie can be judged");
		}
		else {
			confidence = confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(2, RoundingMode.HALF_UP);
		}
	}

	/** A selfie that could be judged. */
	public LivenessResult(BigDecimal confidence) {
		this(confidence, null);
	}

	/** A selfie that cannot be judged because of how it was taken; the customer has to take it again. */
	public static LivenessResult unusable(SelfieProblem problem) {
		return new LivenessResult(null, Objects.requireNonNull(problem));
	}

	public boolean isUsable() {
		return problem == null;
	}

}
