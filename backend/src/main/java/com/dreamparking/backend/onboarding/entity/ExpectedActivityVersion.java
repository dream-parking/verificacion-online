package com.dreamparking.backend.onboarding.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.entity.TransactionType;

/**
 * One version of the declared transactional profile (VDI-90): what the customer declared at that moment. A change
 * is a new version, never an edit; the database rejects updating or deleting one.
 */
@Entity
@Table(name = "expected_activity_version")
public class ExpectedActivityVersion {

	@EmbeddedId
	private ExpectedActivityVersionId id;

	@MapsId("requestId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id")
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transaction_type_code", nullable = false, updatable = false)
	private TransactionType transactionType;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "monthly_amount_range_code", nullable = false, updatable = false)
	private MonthlyAmountRange monthlyAmountRange;

	@CreationTimestamp
	@Column(name = "registered_at", nullable = false, updatable = false)
	private Instant registeredAt;

	protected ExpectedActivityVersion() {
	}

	public ExpectedActivityVersion(OnboardingRequest request, short version, TransactionType transactionType,
			MonthlyAmountRange monthlyAmountRange) {
		this.id = new ExpectedActivityVersionId(request.getId(), version);
		this.request = request;
		this.transactionType = transactionType;
		this.monthlyAmountRange = monthlyAmountRange;
	}

	public ExpectedActivityVersionId getId() {
		return id;
	}

	public short getVersion() {
		return id.getVersion();
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public MonthlyAmountRange getMonthlyAmountRange() {
		return monthlyAmountRange;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

}
