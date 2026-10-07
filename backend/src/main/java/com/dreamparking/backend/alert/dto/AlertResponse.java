package com.dreamparking.backend.alert.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.alert.entity.AlertInboxItem;
import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;

/** Open alert in the analyst inbox. {@code account} is masked ({@code •••• 4821}); the assignee is null while unassigned. */
public record AlertResponse(UUID id, AlertCriticality criticality, String account, String reason,
		AlertStatus status, UUID assigneeId, String assigneeName, Instant raisedAt) {

	public static AlertResponse of(AlertInboxItem item) {
		return new AlertResponse(item.getId(), item.getCriticality(), item.getMaskedAccount(), item.getReason(),
				item.getStatus(), item.getAssigneeId(), item.getAssigneeName(), item.getRaisedAt());
	}

}
