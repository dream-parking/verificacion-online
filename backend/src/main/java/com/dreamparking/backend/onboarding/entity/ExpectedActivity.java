package com.dreamparking.backend.onboarding.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.dreamparking.backend.catalog.entity.TransactionType;

/** Expected monthly account activity (step 3), one per request. Input of score rule R-01. */
@Entity
@Table(name = "movimiento_esperado")
public class ExpectedActivity {

	@Id
	@Column(name = "solicitud_id")
	private UUID requestId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitud_id")
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tipo_codigo", nullable = false)
	private TransactionType transactionType;

	@Column(name = "monto_mensual_usd", nullable = false, precision = 14, scale = 2)
	private BigDecimal monthlyAmountUsd;

	@CreationTimestamp
	@Column(name = "registrado_en", nullable = false)
	private Instant registeredAt;

	@UpdateTimestamp
	@Column(name = "actualizado_en", nullable = false)
	private Instant updatedAt;

	public UUID getRequestId() {
		return requestId;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public void setRequest(OnboardingRequest request) {
		this.request = request;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public void setTransactionType(TransactionType transactionType) {
		this.transactionType = transactionType;
	}

	public BigDecimal getMonthlyAmountUsd() {
		return monthlyAmountUsd;
	}

	public void setMonthlyAmountUsd(BigDecimal monthlyAmountUsd) {
		this.monthlyAmountUsd = monthlyAmountUsd;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
