package com.dreamparking.backend.onboarding.dto;

import java.time.Instant;
import java.util.Map;

import com.dreamparking.backend.onboarding.entity.RequestEvent;
import com.dreamparking.backend.onboarding.entity.enums.RequestEventType;

/** Entry of a request timeline. */
public record RequestEventResponse(RequestEventType type, String description, Map<String, Object> data,
		String actor, Instant occurredAt) {

	public static RequestEventResponse of(RequestEvent event) {
		return new RequestEventResponse(event.getType(), event.getDescription(), event.getData(), event.getActor(),
				event.getOccurredAt());
	}

}
