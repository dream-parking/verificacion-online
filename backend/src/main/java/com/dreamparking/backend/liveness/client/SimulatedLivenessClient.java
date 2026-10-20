package com.dreamparking.backend.liveness.client;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.dreamparking.backend.identity.ocr.DocumentPhoto;

/**
 * Stand-in provider for development and demos ({@code app.liveness.provider=simulated}): it does not look at the
 * selfie and always answers the same confidence ({@code app.liveness.simulated-confidence}). It must never be the
 * provider of a real environment, because every selfie would pass.
 */
@Component
@ConditionalOnProperty(name = "app.liveness.provider", havingValue = "simulated")
public class SimulatedLivenessClient implements LivenessClient {

	static final String PROVIDER = "simulated";

	private final BigDecimal confidence;

	public SimulatedLivenessClient(@Value("${app.liveness.simulated-confidence:0.90}") BigDecimal confidence) {
		this.confidence = confidence;
	}

	@Override
	public LivenessResult check(DocumentPhoto selfie) {
		return new LivenessResult(confidence);
	}

	@Override
	public String provider() {
		return PROVIDER;
	}

}
