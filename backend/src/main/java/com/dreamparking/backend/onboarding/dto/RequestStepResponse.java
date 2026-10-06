package com.dreamparking.backend.onboarding.dto;

import java.time.Instant;

import com.dreamparking.backend.onboarding.entity.RequestStep;
import com.dreamparking.backend.onboarding.entity.enums.OnboardingStep;

/** Time spent on one step of a request. */
public record RequestStepResponse(OnboardingStep step, Instant startedAt, Instant completedAt,
		Integer durationSeconds, Short attempts) {

	public static RequestStepResponse of(RequestStep step) {
		return new RequestStepResponse(step.getStep(), step.getStartedAt(), step.getCompletedAt(),
				step.getDurationSeconds(), step.getAttempts());
	}

}
