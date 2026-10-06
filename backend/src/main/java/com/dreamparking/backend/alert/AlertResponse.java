package com.dreamparking.backend.alert;

import java.time.Instant;
import java.util.UUID;

/** Open alert in the analyst inbox. {@code account} is masked ({@code •••• 4821}); the assignee is null while unassigned. */
public record AlertResponse(UUID id, AlertCriticality criticality, String account, String reason, AlertStatus status,
		UUID assigneeId, String assigneeName, Instant raisedAt) {

	static AlertResponse of(AlertInboxItem item) {
		return new AlertResponse(item.getId(), item.getCriticality(), item.getMaskedAccount(), item.getReason(),
				item.getStatus(), item.getAssigneeId(), item.getAssigneeName(), item.getRaisedAt());
	}

}
