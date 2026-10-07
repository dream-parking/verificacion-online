package com.dreamparking.backend.alert.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.account.entity.Account;
import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.console.entity.ConsoleUser;

/** Fraud/AML alert raised on an account. */
@Entity
@Table(name = "alert")
public class Alert {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "type_code", nullable = false)
	private AlertType type;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "criticality", nullable = false, columnDefinition = "alert_criticality")
	private AlertCriticality criticality;

	@Generated(event = {EventType.INSERT, EventType.UPDATE})
	@Column(name = "severity", insertable = false, updatable = false)
	private Short severity;

	@Column(name = "reason", nullable = false, length = 250)
	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "evidence", nullable = false)
	private Map<String, Object> evidence = new HashMap<>();

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false, columnDefinition = "alert_status")
	private AlertStatus status = AlertStatus.UNASSIGNED;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private ConsoleUser assignee;

	@CreationTimestamp
	@Column(name = "raised_at", nullable = false)
	private Instant raisedAt;

	@Column(name = "assigned_at")
	private Instant assignedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "resolution", length = 30)
	private String resolution;

	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	public UUID getId() {
		return id;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public AlertType getType() {
		return type;
	}

	public void setType(AlertType type) {
		this.type = type;
	}

	public AlertCriticality getCriticality() {
		return criticality;
	}

	public void setCriticality(AlertCriticality criticality) {
		this.criticality = criticality;
	}

	public Short getSeverity() {
		return severity;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Map<String, Object> getEvidence() {
		return evidence;
	}

	public void setEvidence(Map<String, Object> evidence) {
		this.evidence = evidence;
	}

	public AlertStatus getStatus() {
		return status;
	}

	public void setStatus(AlertStatus status) {
		this.status = status;
	}

	public ConsoleUser getAssignee() {
		return assignee;
	}

	public void setAssignee(ConsoleUser assignee) {
		this.assignee = assignee;
	}

	public Instant getRaisedAt() {
		return raisedAt;
	}

	public Instant getAssignedAt() {
		return assignedAt;
	}

	public void setAssignedAt(Instant assignedAt) {
		this.assignedAt = assignedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

	public String getResolution() {
		return resolution;
	}

	public void setResolution(String resolution) {
		this.resolution = resolution;
	}

	public Integer getVersion() {
		return version;
	}

}
