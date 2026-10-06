package com.dreamparking.backend.alert.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.alert.entity.enums.AlertCriticality;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.common.persistence.PgEnumJdbcType;

/** Open alert in the console inbox (view). */
@Entity
@Immutable
@Table(name = "v_bandeja_alertas")
public class AlertInboxItem {

	@Id
	@Column(name = "id")
	private UUID id;

	@Convert(converter = AlertCriticality.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "criticidad", nullable = false, columnDefinition = "criticidad_alerta")
	private AlertCriticality criticality;

	@Column(name = "severidad")
	private Short severity;

	@Column(name = "cuenta", columnDefinition = "text")
	private String maskedAccount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "ultimos4", length = 4)
	private String lastFour;

	@Column(name = "motivo", length = 250)
	private String reason;

	@Convert(converter = AlertStatus.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "estado", nullable = false, columnDefinition = "estado_alerta")
	private AlertStatus status;

	@Column(name = "responsable", columnDefinition = "text")
	private String assigneeName;

	@Column(name = "responsable_id")
	private UUID assigneeId;

	@Column(name = "generada_en")
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
