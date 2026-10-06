package com.dreamparking.backend.alert.entity;

import java.time.Instant;

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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.console.entity.ConsoleUser;

/** Status change of an alert. */
@Entity
@Table(name = "alert_history")
public class AlertHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alert_id", nullable = false)
	private Alert alert;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "previous_status", columnDefinition = "alert_status")
	private AlertStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "new_status", nullable = false, columnDefinition = "alert_status")
	private AlertStatus newStatus;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private ConsoleUser assignee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private ConsoleUser actor;

	@Column(name = "comment", columnDefinition = "text")
	private String comment;

	@CreationTimestamp
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	public Long getId() {
		return id;
	}

	public Alert getAlert() {
		return alert;
	}

	public void setAlert(Alert alert) {
		this.alert = alert;
	}

	public AlertStatus getPreviousStatus() {
		return previousStatus;
	}

	public void setPreviousStatus(AlertStatus previousStatus) {
		this.previousStatus = previousStatus;
	}

	public AlertStatus getNewStatus() {
		return newStatus;
	}

	public void setNewStatus(AlertStatus newStatus) {
		this.newStatus = newStatus;
	}

	public ConsoleUser getAssignee() {
		return assignee;
	}

	public void setAssignee(ConsoleUser assignee) {
		this.assignee = assignee;
	}

	public ConsoleUser getActor() {
		return actor;
	}

	public void setActor(ConsoleUser actor) {
		this.actor = actor;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
