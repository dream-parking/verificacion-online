package com.dreamparking.backend.onboarding.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.onboarding.entity.RequestSignals;
import com.dreamparking.backend.onboarding.entity.enums.TypingPace;

/** Session and device signals of a request, from its latest session. */
public record RequestSignalsResponse(UUID requestId, String ip, String approximateLocation, String deviceFingerprint,
		String device, Short typingSpeedCpm, TypingPace typingPace, Instant startedAt, Instant submittedAt,
		Boolean nightTime, Long totalDurationSeconds, Long requestsFromSameDevice) {

	public static RequestSignalsResponse of(RequestSignals signals) {
		return new RequestSignalsResponse(signals.getRequestId(),
				signals.getIp() == null ? null : signals.getIp().getHostAddress(), signals.getApproximateLocation(),
				signals.getDeviceFingerprint(), signals.getDevice(), signals.getTypingSpeedCpm(),
				signals.getTypingPace(), signals.getStartedAt(), signals.getSubmittedAt(), signals.getNightTime(),
				signals.getTotalDurationSeconds(), signals.getRequestsFromSameDevice());
	}

}
