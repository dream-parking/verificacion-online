package com.dreamparking.backend.risk.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.dreamparking.backend.risk.entity.ScoreModel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;

/**
 * Score model in force: the score ranges and the points each signal can add. Signals the model gives no weight yet
 * are listed with a null {@code maxPoints}.
 */
public record ScoreModelResponse(short version, String name, Range low, Range medium, Range high, Instant validFrom,
		List<Weight> weights) {

	/** Scores from {@code from} to {@code to}, both included. */
	public record Range(int from, int to) {
	}

	public record Weight(ScoreSignal signal, String name, Integer maxPoints) {
	}

	public static ScoreModelResponse of(ScoreModel model) {
		return new ScoreModelResponse(model.getVersion(), model.getName(), new Range(0, model.getLowMax()),
				new Range(model.getLowMax() + 1, model.getMediumMax()), new Range(model.getMediumMax() + 1, 100),
				model.getValidFrom(),
				Arrays.stream(ScoreSignal.values())
					.map(signal -> new Weight(signal, signal.label(), model.weightOf(signal)))
					.toList());
	}

}
