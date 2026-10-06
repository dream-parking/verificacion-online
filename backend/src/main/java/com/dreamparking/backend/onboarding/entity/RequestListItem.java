package com.dreamparking.backend.onboarding.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Synchronize;
import org.hibernate.annotations.JdbcType;

import com.dreamparking.backend.common.persistence.PgEnumJdbcType;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Row of the console request list (view). */
@Entity
@Immutable
// Tables the view reads: Hibernate flushes pending changes to them before querying it.
@Synchronize({ "solicitud", "movimiento_esperado", "cat_tipo_movimiento" })
@Table(name = "v_solicitud_listado")
public class RequestListItem {

	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "numero", length = 14)
	private String number;

	@Column(name = "nombre", columnDefinition = "text")
	private String name;

	@Column(name = "fecha")
	private Instant date;

	@Column(name = "tipo_dinero", length = 80)
	private String transactionTypeLabel;

	@Column(name = "monto_mensual_usd", precision = 14, scale = 2)
	private BigDecimal monthlyAmountUsd;

	@Convert(converter = RiskLevel.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "nivel_riesgo", nullable = false, columnDefinition = "nivel_riesgo")
	private RiskLevel riskLevel;

	@Convert(converter = RequestStatus.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "estado", nullable = false, columnDefinition = "estado_solicitud")
	private RequestStatus status;

	@Column(name = "etapas_completadas")
	private Short completedSteps;

	public UUID getId() {
		return id;
	}

	public String getNumber() {
		return number;
	}

	public String getName() {
		return name;
	}

	public Instant getDate() {
		return date;
	}

	public String getTransactionTypeLabel() {
		return transactionTypeLabel;
	}

	public BigDecimal getMonthlyAmountUsd() {
		return monthlyAmountUsd;
	}

	public RiskLevel getRiskLevel() {
		return riskLevel;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public Short getCompletedSteps() {
		return completedSteps;
	}

}
