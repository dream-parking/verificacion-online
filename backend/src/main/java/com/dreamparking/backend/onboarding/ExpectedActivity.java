package com.dreamparking.backend.onboarding;

import com.dreamparking.backend.catalog.MonthlyAmountRange;
import com.dreamparking.backend.catalog.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Expected account activity (step 3), one per request: type of money and the chosen monthly amount range. Input of score rule R-01. */
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

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rango_monto_codigo", nullable = false)
	private MonthlyAmountRange monthlyAmountRange;

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

	public MonthlyAmountRange getMonthlyAmountRange() {
		return monthlyAmountRange;
	}

	public void setMonthlyAmountRange(MonthlyAmountRange monthlyAmountRange) {
		this.monthlyAmountRange = monthlyAmountRange;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
