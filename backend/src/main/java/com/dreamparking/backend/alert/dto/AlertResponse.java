package com.dreamparking.backend.alert.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.dreamparking.backend.alert.entity.Alert;
import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.console.entity.ConsoleUser;

public record AlertResponse(UUID id, UUID accountId, String maskedAccount, String typeCode, String typeDescription,
		AlertCriticality criticality, String reason, Map<String, Object> evidence, AlertStatus status,
		UUID assigneeId, String assigneeName, Instant raisedAt, Instant assignedAt, Instant closedAt,
		String resolution) {

	public static AlertResponse of(Alert alert) {
		ConsoleUser assignee = alert.getAssignee();
		return new AlertResponse(alert.getId(), alert.getAccount().getId(), "•••• " + alert.getAccount().getLastFour(),
				alert.getType().getCode(), alert.getType().getDescription(), alert.getCriticality(), alert.getReason(),
				alert.getEvidence(), alert.getStatus(), assignee == null ? null : assignee.getId(),
				assignee == null ? null : assignee.getFullName(), alert.getRaisedAt(), alert.getAssignedAt(),
				alert.getClosedAt(), alert.getResolution());
	}

}
