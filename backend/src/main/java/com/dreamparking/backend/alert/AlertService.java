package com.dreamparking.backend.alert;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.InvalidInputException;
import com.dreamparking.backend.common.InvalidStateException;
import com.dreamparking.backend.common.NotFoundException;

/** Analyst inbox: ordered, filterable list of open alerts and the "take alert" action. */
@Service
public class AlertService {

	/** Dates of the filters are calendar days in El Salvador, like the console shows them. */
	static final ZoneId LOCAL_ZONE = ZoneId.of("America/El_Salvador");

	private final AlertInboxItemRepository inbox;

	private final AlertRepository alerts;

	private final EntityManager entityManager;

	public AlertService(AlertInboxItemRepository inbox, AlertRepository alerts, EntityManager entityManager) {
		this.inbox = inbox;
		this.alerts = alerts;
		this.entityManager = entityManager;
	}

	/**
	 * Open alerts, most severe first and newest first within the same severity. {@code account} matches the account's
	 * last four digits; {@code from} and {@code to} are inclusive days.
	 */
	@Transactional(readOnly = true)
	public List<AlertResponse> list(AlertStatus status, UUID assigneeId, String account, LocalDate from,
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

	/** Assigns an unassigned alert to the user; two analysts taking the same alert cannot both succeed. */
	@Transactional
	public AlertResponse take(UUID alertId, UUID userId) {
		if (!alerts.existsById(alertId)) {
			throw new NotFoundException("Alert not found: " + alertId);
		}
		if (!alerts.take(alertId, userId)) {
			throw new InvalidStateException("Alert " + alertId + " is already assigned or closed");
		}
		// The database function changed the row behind the persistence context's back: re-read the view row.
		AlertInboxItem item = inbox.findById(alertId).orElseThrow();
		entityManager.refresh(item);
		return AlertResponse.of(item);
	}

}
