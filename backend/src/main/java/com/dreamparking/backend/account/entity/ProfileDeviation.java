package com.dreamparking.backend.account.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

/**
 * Tracking of the declared profile (VDI-3): a deviation found in a month of an account, with the declared and the real
 * value and the alert it raised. Recorded once per account, month and kind, so it alerts only the first time. It keeps
 * the tolerance in force when it was detected (VDI-137), since an administrator can change it later.
 */
@Entity
@Immutable
@Table(name = "profile_deviation")
public class ProfileDeviation {

	@EmbeddedId
	private ProfileDeviationId id;

	/** What the customer declared; null when the deviation is about something not declared (a type of money). */
	@Column(name = "declared_value", precision = 14, scale = 2)
	private BigDecimal declaredValue;

	@Column(name = "actual_value", nullable = false, precision = 14, scale = 2)
	private BigDecimal actualValue;

	@Column(name = "detail", nullable = false, length = 250)
	private String detail;

	@Column(name = "alert_id", nullable = false)
	private UUID alertId;

	@Column(name = "tolerance_percent", nullable = false)
	private short tolerancePercent;

	@CreationTimestamp
	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	protected ProfileDeviation() {
	}

	public ProfileDeviation(ProfileDeviationId id, BigDecimal declaredValue, BigDecimal actualValue, String detail,
			UUID alertId, int tolerancePercent) {
		this.id = id;
		this.declaredValue = declaredValue;
		this.actualValue = actualValue;
		this.detail = detail;
		this.alertId = alertId;
		this.tolerancePercent = (short) tolerancePercent;
	}

	public ProfileDeviationId getId() {
		return id;
	}

	public BigDecimal getDeclaredValue() {
		return declaredValue;
	}

	public BigDecimal getActualValue() {
		return actualValue;
	}

	public String getDetail() {
		return detail;
	}

	public UUID getAlertId() {
		return alertId;
	}

	public int getTolerancePercent() {
		return tolerancePercent;
	}

	public Instant getDetectedAt() {
		return detectedAt;
	}

}
