package com.dreamparking.backend.onboarding.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

/**
 * Expected account activity (step 5), one per request: the types of money and how much will move each month. It is the
 * baseline the real account movements are compared against (VDI-3). The range is the input of score rule R-01; the
 * other figures are optional (older app versions only send the type and the range).
 */
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

	/** Other types of money besides {@link #transactionType}, by code. */
	@ElementCollection
	@CollectionTable(name = "expected_activity_extra_type", joinColumns = @JoinColumn(name = "request_id"))
	@Column(name = "transaction_type_code", length = 30)
	private Set<String> extraTransactionTypeCodes = new LinkedHashSet<>();

	/** Estimated monthly amount in USD, inside {@link #monthlyAmountRange}. */
	@Column(name = "monthly_amount_usd", precision = 14, scale = 2)
	private BigDecimal monthlyAmountUsd;

	/** Expected number of incoming movements a month. */
	@Column(name = "monthly_transaction_count")
	private Short monthlyTransactionCount;

	/** Expected money leaving the account a month, in USD. */
	@Column(name = "monthly_outflow_usd", precision = 14, scale = 2)
	private BigDecimal monthlyOutflowUsd;

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

	public Set<String> getExtraTransactionTypeCodes() {
		return extraTransactionTypeCodes;
	}

	public void setExtraTransactionTypeCodes(Set<String> codes) {
		extraTransactionTypeCodes.clear();
		extraTransactionTypeCodes.addAll(codes);
	}

	/** The main type first, then the extra ones. */
	public Set<String> declaredTransactionTypeCodes() {
		Set<String> codes = new LinkedHashSet<>();
		codes.add(transactionType.getCode());
		codes.addAll(extraTransactionTypeCodes);
		return codes;
	}

	public BigDecimal getMonthlyAmountUsd() {
		return monthlyAmountUsd;
	}

	public void setMonthlyAmountUsd(BigDecimal monthlyAmountUsd) {
		this.monthlyAmountUsd = monthlyAmountUsd;
	}

	public Short getMonthlyTransactionCount() {
		return monthlyTransactionCount;
	}

	public void setMonthlyTransactionCount(Short monthlyTransactionCount) {
		this.monthlyTransactionCount = monthlyTransactionCount;
	}

	public BigDecimal getMonthlyOutflowUsd() {
		return monthlyOutflowUsd;
	}

	public void setMonthlyOutflowUsd(BigDecimal monthlyOutflowUsd) {
		this.monthlyOutflowUsd = monthlyOutflowUsd;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
