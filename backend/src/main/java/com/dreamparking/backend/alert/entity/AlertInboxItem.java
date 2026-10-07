package com.dreamparking.backend.alert.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Synchronize;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;

/** Open alert in the console inbox (view). */
@Entity
@Immutable
// Tables the view reads: Hibernate flushes pending changes to them before querying it.
@Synchronize({ "alert", "account", "console_user" })
@Table(name = "v_alert_inbox")
public class AlertInboxItem {

	@Id
	@Column(name = "id")
	private UUID id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "criticality", nullable = false, columnDefinition = "alert_criticality")
	private AlertCriticality criticality;

	@Column(name = "severity")
	private Short severity;

	@Column(name = "masked_account", columnDefinition = "text")
	private String maskedAccount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "last_four", length = 4)
	private String lastFour;

	@Column(name = "reason", length = 250)
	private String reason;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false, columnDefinition = "alert_status")
	private AlertStatus status;

	@Column(name = "assignee_name", columnDefinition = "text")
	private String assigneeName;

	@Column(name = "assignee_id")
	private UUID assigneeId;

	@Column(name = "raised_at")
	private Instant raisedAt;

	public UUID getId() {
		return id;
	}

	public AlertCriticality getCriticality() {
		return criticality;
	}

	public Short getSeverity() {
		return severity;
	}

	public String getMaskedAccount() {
		return maskedAccount;
	}

	public String getLastFour() {
		return lastFour;
	}

	public String getReason() {
		return reason;
	}

	public AlertStatus getStatus() {
		return status;
	}

	public String getAssigneeName() {
		return assigneeName;
	}

	public UUID getAssigneeId() {
		return assigneeId;
	}

	public Instant getRaisedAt() {
		return raisedAt;
	}

}
