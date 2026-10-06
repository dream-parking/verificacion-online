package com.dreamparking.backend.alert.dto;

import java.time.Instant;

import com.dreamparking.backend.alert.entity.AlertHistory;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;

/** Status change of an alert. {@code actorName} is null when the system made the change. */
public record AlertHistoryResponse(AlertStatus previousStatus, AlertStatus newStatus, String assigneeName,
		String actorName, String comment, Instant occurredAt) {

	public static AlertHistoryResponse of(AlertHistory entry) {
		return new AlertHistoryResponse(entry.getPreviousStatus(), entry.getNewStatus(),
				entry.getAssignee() == null ? null : entry.getAssignee().getFullName(),
				entry.getActor() == null ? null : entry.getActor().getFullName(), entry.getComment(),
				entry.getOccurredAt());
	}

}
