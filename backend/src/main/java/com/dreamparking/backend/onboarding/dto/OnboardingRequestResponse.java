package com.dreamparking.backend.onboarding.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Summary of an onboarding request. {@code number} stays null until the request is submitted. */
public record OnboardingRequestResponse(UUID id, String number, RequestStatus status, short completedSteps,
		RiskLevel riskLevel, Instant startedAt, Instant submittedAt) {

	public static OnboardingRequestResponse of(OnboardingRequest request) {
		return new OnboardingRequestResponse(request.getId(), request.getNumber(), request.getStatus(),
				request.getCompletedSteps(), request.getRiskLevel(), request.getStartedAt(), request.getSubmittedAt());
	}

}
