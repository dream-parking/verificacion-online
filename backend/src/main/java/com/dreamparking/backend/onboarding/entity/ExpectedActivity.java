package com.dreamparking.backend.onboarding.entity;

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

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.entity.TransactionType;

/** Expected account activity (step 3), one per request: type of money and the chosen monthly amount range. Input of score rule R-01. */
@Entity
@Table(name = "expected_activity")
public class ExpectedActivity {

	@Id
	@Column(name = "request_id")
	private UUID requestId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id")
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transaction_type_code", nullable = false)
	private TransactionType transactionType;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "monthly_amount_range_code", nullable = false)
	private MonthlyAmountRange monthlyAmountRange;

	@CreationTimestamp
	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
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
