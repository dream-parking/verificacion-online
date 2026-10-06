package com.dreamparking.backend.alert.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.alert.entity.AlertInboxItem;
import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;

/** Open alert in the console inbox. */
public record InboxItemResponse(UUID id, AlertCriticality criticality, String maskedAccount, String reason,
		AlertStatus status, UUID assigneeId, String assigneeName, Instant raisedAt) {

	public static InboxItemResponse of(AlertInboxItem item) {
		return new InboxItemResponse(item.getId(), item.getCriticality(), item.getMaskedAccount(), item.getReason(),
				item.getStatus(), item.getAssigneeId(), item.getAssigneeName(), item.getRaisedAt());
	}

}
