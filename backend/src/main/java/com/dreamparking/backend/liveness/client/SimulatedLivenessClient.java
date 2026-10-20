package com.dreamparking.backend.liveness.client;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.dreamparking.backend.identity.ocr.DocumentPhoto;

/**
 * Stand-in provider for development and demos ({@code app.liveness.provider=simulated}): it does not look at the
 * selfie and always answers the same confidence ({@code app.liveness.simulated-confidence}), or the same problem
 * ({@code app.liveness.simulated-problem}) to try the app's "take it again" flow. It must never be the
 * provider of a real environment, because every selfie would pass.
 */
@Component
@ConditionalOnProperty(name = "app.liveness.provider", havingValue = "simulated")
public class SimulatedLivenessClient implements LivenessClient {

	static final String PROVIDER = "simulated";

	private final BigDecimal confidence;

	private final SelfieProblem problem;

	/**
	 * @param problem a {@link SelfieProblem} name to answer instead of a confidence, so the app can be tried against
	 * the "take it again" flow; blank answers the confidence. An unknown name stops the application at start.
	 */
	public SimulatedLivenessClient(@Value("${app.liveness.simulated-confidence:0.90}") BigDecimal confidence,
			@Value("${app.liveness.simulated-problem:}") String problem) {
		this.confidence = confidence;
		this.problem = problem == null || problem.isBlank() ? null : SelfieProblem.valueOf(problem.trim());
	}

	@Override
	public LivenessResult check(DocumentPhoto selfie) {
		return problem == null ? new LivenessResult(confidence) : LivenessResult.unusable(problem);
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

}
