package com.dreamparking.backend.alert.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.account.service.AccountService;
import com.dreamparking.backend.alert.dto.AlertDetailResponse;
import com.dreamparking.backend.alert.dto.AlertHistoryResponse;
import com.dreamparking.backend.alert.dto.AlertResponse;
import com.dreamparking.backend.alert.dto.AlertTypeResponse;
import com.dreamparking.backend.alert.dto.CloseAlertRequest;
import com.dreamparking.backend.alert.dto.RaiseAlertRequest;
import com.dreamparking.backend.alert.entity.Alert;
import com.dreamparking.backend.alert.entity.AlertHistory;
import com.dreamparking.backend.alert.entity.AlertInboxItem;
import com.dreamparking.backend.alert.entity.AlertType;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.alert.repository.AlertHistoryRepository;
import com.dreamparking.backend.alert.repository.AlertInboxItemRepository;
import com.dreamparking.backend.alert.repository.AlertRepository;
import com.dreamparking.backend.alert.repository.AlertTypeRepository;
import com.dreamparking.backend.common.exception.ForbiddenException;
import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;

/**
 * Alert workflow in the console: UNASSIGNED → ASSIGNED (taken) → IN_REVIEW → CLOSED.
 * Every status change is written to the alert history.
 */
@Service
public class AlertService {

	/** Dates of the inbox filters are calendar days in El Salvador, like the console shows them. */
	static final ZoneId LOCAL_ZONE = ZoneId.of("America/El_Salvador");

	private final AlertRepository alerts;

	private final AlertTypeRepository alertTypes;

	private final AlertHistoryRepository history;

	private final AlertInboxItemRepository inbox;

	private final AccountService accountService;

	private final ConsoleUserService consoleUserService;

	private final AccessAuditService accessAuditService;

	public AlertService(AlertRepository alerts, AlertTypeRepository alertTypes, AlertHistoryRepository history,
			AlertInboxItemRepository inbox, AccountService accountService, ConsoleUserService consoleUserService,
			AccessAuditService accessAuditService) {
		this.alerts = alerts;
		this.alertTypes = alertTypes;
		this.history = history;
		this.inbox = inbox;
		this.accountService = accountService;
		this.consoleUserService = consoleUserService;
		this.accessAuditService = accessAuditService;
	}

	/**
	 * Open alerts, most critical first and newest first within the same criticality. Every filter is optional:
	 * {@code account} matches the account's last four digits; {@code from} and {@code to} are inclusive days.
	 */
	@Transactional(readOnly = true)
	public List<AlertResponse> inbox(AlertStatus status, UUID assigneeId, String account, LocalDate from,
			LocalDate to) {
		if (from != null && to != null && to.isBefore(from)) {
			throw new InvalidInputException("'to' is before 'from'");
		}
		Specification<AlertInboxItem> spec = (root, q, cb) -> cb.conjunction();
		if (status != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
		}
		if (assigneeId != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("assigneeId"), assigneeId));
		}
		if (account != null && !account.isBlank()) {
			String digits = account.replaceAll("\\D", "");
			spec = spec.and((root, q, cb) -> cb.like(root.get("lastFour"), "%" + digits + "%"));
		}
		if (from != null) {
			spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("raisedAt"),
					from.atStartOfDay(LOCAL_ZONE).toInstant()));
		}
		if (to != null) {
			spec = spec.and((root, q, cb) -> cb.lessThan(root.get("raisedAt"),
					to.plusDays(1).atStartOfDay(LOCAL_ZONE).toInstant()));
		}
		return inbox.findAll(spec, Sort.by(Sort.Order.asc("severity"), Sort.Order.desc("raisedAt")))
			.stream()
			.map(AlertResponse::of)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<AlertTypeResponse> types() {
		return alertTypes.findAllByOrderByCode().stream().map(AlertTypeResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public AlertDetailResponse get(UUID alertId) {
		return AlertDetailResponse.of(find(alertId));
	}

	@Transactional(readOnly = true)
	public List<AlertHistoryResponse> history(UUID alertId) {
		find(alertId);
		return history.findByAlertIdOrderByOccurredAt(alertId).stream().map(AlertHistoryResponse::of).toList();
	}

	@Transactional
	public AlertDetailResponse raise(RaiseAlertRequest body) {
		AlertType type = alertTypes.findById(body.typeCode())
			.orElseThrow(() -> new InvalidInputException("Unknown alert type: " + body.typeCode()));

		Alert alert = new Alert();
		alert.setAccount(accountService.find(body.accountId()));
		alert.setType(type);
		alert.setCriticality(body.criticality() == null ? type.getDefaultCriticality() : body.criticality());
		alert.setReason(body.reason());
		alert.setEvidence(body.evidence() == null ? new HashMap<>() : new HashMap<>(body.evidence()));
		alerts.saveAndFlush(alert);

		recordHistory(alert, null, AlertStatus.UNASSIGNED, null, null, null);
		return AlertDetailResponse.of(alert);
	}

	/**
	 * Assigns the alert to the user. Atomic: if two analysts take it at the same time, only the first one
	 * gets it and the other receives 409.
	 */
	@Transactional
	public AlertResponse take(UUID alertId, UUID userId, String comment) {
		ConsoleUser user = consoleUserService.activeUser(userId);
		int taken = alerts.takeIfUnassigned(alertId, user, Instant.now(), AlertStatus.ASSIGNED, AlertStatus.UNASSIGNED);
		if (taken == 0) {
			find(alertId);
			throw new InvalidStateException("Alert " + alertId + " is already assigned or closed");
		}

		Alert alert = find(alertId);
		recordHistory(alert, AlertStatus.UNASSIGNED, AlertStatus.ASSIGNED, alert.getAssignee(), alert.getAssignee(),
				comment);
		accessAuditService.record(alert.getAssignee(), "TAKE_ALERT", "alert", alertId.toString(), null);
		return AlertResponse.of(inbox.findById(alertId).orElseThrow());
	}

	/** The assignee starts working on the alert. */
	@Transactional
	public AlertDetailResponse startReview(UUID alertId, UUID userId, String comment) {
		ConsoleUser user = consoleUserService.activeUser(userId);
		Alert alert = find(alertId);
		if (alert.getStatus() != AlertStatus.ASSIGNED) {
			throw new InvalidStateException("Alert " + alertId + " is " + alert.getStatus() + ", not ASSIGNED");
		}
		if (!user.getId().equals(alert.getAssignee().getId())) {
			throw new ForbiddenException("Only the assignee can start reviewing the alert");
		}

		alert.setStatus(AlertStatus.IN_REVIEW);
		recordHistory(alert, AlertStatus.ASSIGNED, AlertStatus.IN_REVIEW, alert.getAssignee(), user, comment);
		return AlertDetailResponse.of(alert);
	}

	/** Closes the alert with a resolution. Allowed to the assignee, the KYC lead and admins. */
	@Transactional
	public AlertDetailResponse close(UUID alertId, UUID userId, CloseAlertRequest body) {
		ConsoleUser user = consoleUserService.activeUser(userId);
		Alert alert = find(alertId);
		AlertStatus previous = alert.getStatus();
		if (previous != AlertStatus.ASSIGNED && previous != AlertStatus.IN_REVIEW) {
			throw new InvalidStateException("Alert " + alertId + " is " + previous + " and cannot be closed");
		}
		boolean isAssignee = user.getId().equals(alert.getAssignee().getId());
		if (!isAssignee && user.getRole() == ConsoleRole.FRAUD_ANALYST) {
			throw new ForbiddenException("Only the assignee, the KYC lead or an admin can close the alert");
		}

		alert.setStatus(AlertStatus.CLOSED);
		alert.setClosedAt(Instant.now());
		alert.setResolution(body.resolution());
		recordHistory(alert, previous, AlertStatus.CLOSED, alert.getAssignee(), user, body.comment());
		return AlertDetailResponse.of(alert);
	}

	private Alert find(UUID alertId) {
		return alerts.findById(alertId).orElseThrow(() -> new NotFoundException("Alert not found: " + alertId));
	}

	private void recordHistory(Alert alert, AlertStatus previous, AlertStatus next, ConsoleUser assignee,
			ConsoleUser actor, String comment) {
		AlertHistory entry = new AlertHistory();
		entry.setAlert(alert);
		entry.setPreviousStatus(previous);
		entry.setNewStatus(next);
		entry.setAssignee(assignee);
		entry.setActor(actor);
		entry.setComment(comment);
		history.save(entry);
	}

}
