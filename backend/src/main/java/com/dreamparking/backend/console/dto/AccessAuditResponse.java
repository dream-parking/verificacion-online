package com.dreamparking.backend.console.dto;

import java.time.Instant;

import com.dreamparking.backend.console.entity.AccessAudit;

public record AccessAuditResponse(Long id, String action, String entityName, String entityId, String ip,
		Instant occurredAt) {

	public static AccessAuditResponse of(AccessAudit audit) {
		return new AccessAuditResponse(audit.getId(), audit.getAction(), audit.getEntityName(), audit.getEntityId(),
				audit.getIp() == null ? null : audit.getIp().getHostAddress(), audit.getOccurredAt());
	}

}
