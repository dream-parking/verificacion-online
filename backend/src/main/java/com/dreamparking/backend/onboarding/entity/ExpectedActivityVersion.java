package com.dreamparking.backend.onboarding.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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

import com.dreamparking.backend.catalog.entity.MonthlyAmountRange;
import com.dreamparking.backend.catalog.entity.TransactionType;
import com.dreamparking.backend.onboarding.entity.enums.ProfileChangedBy;

/**
 * One version of the declared profile (VDI-3): the expected activity as the customer declared it at one moment.
 * {@link ExpectedActivity} is the version in force; this keeps every version, which the database does not let anyone
 * change or delete. The version in force is the highest one of the request.
 */
@Entity
@Table(name = "expected_activity_version")
public class ExpectedActivityVersion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private OnboardingRequest request;

	@Column(name = "version", nullable = false, updatable = false)
	private short version;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transaction_type_code", nullable = false, updatable = false)
	private TransactionType transactionType;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "monthly_amount_range_code", nullable = false, updatable = false)
	private MonthlyAmountRange monthlyAmountRange;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "extra_type_codes", nullable = false, updatable = false)
	private String[] extraTypeCodes = new String[0];

	@Column(name = "monthly_amount_usd", precision = 14, scale = 2, updatable = false)
	private BigDecimal monthlyAmountUsd;

	@Column(name = "monthly_transaction_count", updatable = false)
	private Short monthlyTransactionCount;

	@Column(name = "monthly_outflow_usd", precision = 14, scale = 2, updatable = false)
	private BigDecimal monthlyOutflowUsd;

	@Enumerated(EnumType.STRING)
	@Column(name = "changed_by", nullable = false, length = 20, updatable = false)
	private ProfileChangedBy changedBy;

	@CreationTimestamp
	@Column(name = "declared_at", nullable = false, updatable = false)
	private Instant declaredAt;

	protected ExpectedActivityVersion() {
	}

	/** Version {@code version} with what {@code activity} holds now. */
	public ExpectedActivityVersion(ExpectedActivity activity, short version, ProfileChangedBy changedBy) {
		this.request = activity.getRequest();
		this.version = version;
		this.transactionType = activity.getTransactionType();
		this.monthlyAmountRange = activity.getMonthlyAmountRange();
		this.extraTypeCodes = activity.getExtraTransactionTypeCodes().toArray(String[]::new);
		this.monthlyAmountUsd = activity.getMonthlyAmountUsd();
		this.monthlyTransactionCount = activity.getMonthlyTransactionCount();
		this.monthlyOutflowUsd = activity.getMonthlyOutflowUsd();
		this.changedBy = changedBy;
	}

	/** Whether {@code activity} declares the same as this version (the order of the extra types does not matter). */
	public boolean declaresTheSameAs(ExpectedActivity activity) {
		return transactionType.getCode().equals(activity.getTransactionType().getCode())
				&& monthlyAmountRange.getCode().equals(activity.getMonthlyAmountRange().getCode())
				&& extraTypeCodes.length == activity.getExtraTransactionTypeCodes().size()
				&& activity.getExtraTransactionTypeCodes().containsAll(List.of(extraTypeCodes))
				&& sameAmount(monthlyAmountUsd, activity.getMonthlyAmountUsd())
				&& Objects.equals(monthlyTransactionCount, activity.getMonthlyTransactionCount())
				&& sameAmount(monthlyOutflowUsd, activity.getMonthlyOutflowUsd());
	}

	private static boolean sameAmount(BigDecimal a, BigDecimal b) {
		return a == null ? b == null : b != null && a.compareTo(b) == 0;
	}

	public Long getId() {
		return id;
	}

	public UUID getRequestId() {
		return request.getId();
	}

	public short getVersion() {
		return version;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public MonthlyAmountRange getMonthlyAmountRange() {
		return monthlyAmountRange;
	}

	public List<String> getExtraTypeCodes() {
		return List.of(extraTypeCodes);
	}

	public BigDecimal getMonthlyAmountUsd() {
		return monthlyAmountUsd;
	}

	public Short getMonthlyTransactionCount() {
		return monthlyTransactionCount;
	}

	public BigDecimal getMonthlyOutflowUsd() {
		return monthlyOutflowUsd;
	}

	public ProfileChangedBy getChangedBy() {
		return changedBy;
	}

	public Instant getDeclaredAt() {
		return declaredAt;
	}

}
