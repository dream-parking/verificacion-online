package com.dreamparking.backend.onboarding.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.onboarding.entity.OnboardingSession;

public record SessionResponse(UUID id, UUID requestId, UUID deviceId, Instant startedAt, Instant endedAt) {

	public static SessionResponse of(OnboardingSession session) {
		return new SessionResponse(session.getId(), session.getRequest().getId(), session.getDevice().getId(),
				session.getStartedAt(), session.getEndedAt());
	}

}
