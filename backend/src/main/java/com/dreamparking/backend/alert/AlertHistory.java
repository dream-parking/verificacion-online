package com.dreamparking.backend.alert;

import com.dreamparking.backend.common.PgEnumJdbcType;
import com.dreamparking.backend.console.ConsoleUser;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;

/** Status change of an alert. */
@Entity
@Table(name = "alerta_historial")
public class AlertHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "alerta_id", nullable = false)
	private Alert alert;

	@Convert(converter = AlertStatus.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "estado_anterior", columnDefinition = "estado_alerta")
	private AlertStatus previousStatus;

	@Convert(converter = AlertStatus.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "estado_nuevo", nullable = false, columnDefinition = "estado_alerta")
	private AlertStatus newStatus;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responsable_id")
	private ConsoleUser assignee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private ConsoleUser actor;

	@Column(name = "comentario", columnDefinition = "text")
	private String comment;

	@CreationTimestamp
	@Column(name = "ocurrido_en", nullable = false)
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
