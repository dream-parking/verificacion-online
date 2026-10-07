package com.dreamparking.backend.alert.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.alert.dto.AlertTypeResponse;
import com.dreamparking.backend.alert.dto.CreateAlertTypeRequest;
import com.dreamparking.backend.alert.dto.UpdateAlertTypeRequest;
import com.dreamparking.backend.alert.entity.AlertType;
import com.dreamparking.backend.alert.repository.AlertTypeRepository;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;

/** Maintenance of the alert type catalog by administrators. */
@Service
public class AlertTypeService {

	private final AlertTypeRepository alertTypes;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public AlertTypeService(AlertTypeRepository alertTypes, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.alertTypes = alertTypes;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	@Transactional
	public AlertTypeResponse create(CreateAlertTypeRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		if (alertTypes.existsById(body.code())) {
			throw new InvalidStateException("The alert type " + body.code() + " already exists");
		}
		AlertType type = new AlertType();
		type.setCode(body.code());
		type.setDescription(body.description().trim());
		type.setDefaultCriticality(body.defaultCriticality());
		AlertType saved = alertTypes.save(type);

		accessAuditService.record(admin, "ALERT_TYPE_CREATE", "alert-type", saved.getCode(), null);
		return AlertTypeResponse.of(saved);
	}

	@Transactional
	public AlertTypeResponse update(String code, UpdateAlertTypeRequest body, UUID adminId) {
		ConsoleUser admin = consoleUserService.activeUser(adminId);
		AlertType type = alertTypes.findById(code)
			.orElseThrow(() -> new NotFoundException("Alert type not found: " + code));
		type.setDescription(body.description().trim());
		type.setDefaultCriticality(body.defaultCriticality());

		accessAuditService.record(admin, "ALERT_TYPE_UPDATE", "alert-type", code, null);
		return AlertTypeResponse.of(type);
	}

}
