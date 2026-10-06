package com.dreamparking.backend.console.service;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.console.dto.AccessAuditResponse;
import com.dreamparking.backend.console.entity.AccessAudit;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.repository.AccessAuditRepository;

/** Audit trail of what console users do with customer data (KYC requirement). */
@Service
public class AccessAuditService {

	private final AccessAuditRepository audits;

	public AccessAuditService(AccessAuditRepository audits) {
		this.audits = audits;
	}

	@Transactional
	public void record(ConsoleUser user, String action, String entityName, String entityId, InetAddress ip) {
		AccessAudit audit = new AccessAudit();
		audit.setUser(user);
		audit.setAction(action);
		audit.setEntityName(entityName);
		audit.setEntityId(entityId);
		audit.setIp(ip);
		audits.save(audit);
	}

	@Transactional(readOnly = true)
	public List<AccessAuditResponse> byUser(UUID userId) {
		return audits.findByUserIdOrderByOccurredAtDesc(userId).stream().map(AccessAuditResponse::of).toList();
	}

}
