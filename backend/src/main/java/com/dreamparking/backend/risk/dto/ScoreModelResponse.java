package com.dreamparking.backend.risk.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;

/**
 * A version of the score model: the score ranges and the points each signal can add. Signals the model gives no weight
 * yet are listed with a null {@code maxPoints}. {@code validTo} is null while the version is in force;
 * {@code publishedBy} is the console user who published it, or null when a migration did.
 */
public record ScoreModelResponse(short version, String name, Range low, Range medium, Range high, Instant validFrom,
		Instant validTo, boolean inForce, String publishedBy, List<Weight> weights) {

	/** Scores from {@code from} to {@code to}, both included. */
	public record Range(int from, int to) {
	}

	public record Weight(ScoreSignal signal, String name, Integer maxPoints) {
	}

	public static ScoreModelResponse of(ScoreModel model) {
		return new ScoreModelResponse(model.getVersion(), model.getName(), new Range(0, model.getLowMax()),
				new Range(model.getLowMax() + 1, model.getMediumMax()), new Range(model.getMediumMax() + 1, 100),
				model.getValidFrom(), model.getValidTo(), model.getValidTo() == null,
				model.getPublishedBy() == null ? null : model.getPublishedBy().getFullName(),
				Arrays.stream(ScoreSignal.values())
					.map(signal -> new Weight(signal, signal.label(), model.weightOf(signal)))
					.toList());
	}

}
