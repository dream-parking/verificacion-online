package com.dreamparking.backend.onboarding.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Synchronize;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Row of the console request list (view). */
@Entity
@Immutable
// Tables the view reads: Hibernate flushes pending changes to them before querying it.
@Synchronize({ "onboarding_request", "expected_activity", "transaction_type", "monthly_amount_range" })
@Table(name = "v_request_list")
public class RequestListItem {

	@Id
	@Column(name = "id")
	private UUID id;

	@Column(name = "number", length = 14)
	private String number;

	@Column(name = "name", columnDefinition = "text")
	private String name;

	@Column(name = "activity_date")
	private Instant date;

	@Column(name = "transaction_type_label", length = 80)
	private String transactionTypeLabel;

	@Column(name = "monthly_amount_range_label", length = 80)
	private String monthlyAmountRangeLabel;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "risk_level", nullable = false, columnDefinition = "risk_level")
	private RiskLevel riskLevel;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false, columnDefinition = "request_status")
	private RequestStatus status;

	@Column(name = "completed_steps")
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

	public String getMonthlyAmountRangeLabel() {
		return monthlyAmountRangeLabel;
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
