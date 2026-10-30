package com.dreamparking.backend.onboarding.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.onboarding.entity.enums.EscalationChannel;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/**
 * Summary of an onboarding request. {@code number} stays null until the request is submitted. An escalated request
 * says the channel, never the score nor the criteria (VDI-143).
 */
public record OnboardingRequestResponse(UUID id, String number, RequestStatus status, short completedSteps,
		RiskLevel riskLevel, Instant startedAt, Instant submittedAt,
		@Schema(description = "Canal al que se escaló la solicitud (VIDEO_CALL: videollamada, BRANCH: agencia); vacío si no está escalada") EscalationChannel escalationChannel) {

	public static OnboardingRequestResponse of(OnboardingRequest request) {
		return new OnboardingRequestResponse(request.getId(), request.getNumber(), request.getStatus(),
				request.getCompletedSteps(), request.getRiskLevel(), request.getStartedAt(), request.getSubmittedAt(),
				request.getEscalationChannel());
	}

}
