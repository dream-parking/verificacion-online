package com.dreamparking.backend.outcome.entity;

import java.time.Instant;

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

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.outcome.entity.enums.CaseOutcome;

/**
 * The outcome of a case (VDI-144). Insert-only: a correction is a new row and the last one of a request is the current
 * outcome. The database refuses to change or delete a row.
 */
@Entity
@Table(name = "request_outcome")
public class RequestOutcome {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private OnboardingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, length = 20, updatable = false)
	private CaseOutcome outcome;

	@Column(name = "reason", nullable = false, length = 250, updatable = false)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registered_by", nullable = false, updatable = false)
	private ConsoleUser registeredBy;

	@CreationTimestamp
	@Column(name = "registered_at", nullable = false, updatable = false)
	private Instant registeredAt;

	protected RequestOutcome() {
	}

	public RequestOutcome(OnboardingRequest request, CaseOutcome outcome, String reason, ConsoleUser registeredBy) {
		this.request = request;
		this.outcome = outcome;
		this.reason = reason;
		this.registeredBy = registeredBy;
	}

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public CaseOutcome getOutcome() {
		return outcome;
	}

	public String getReason() {
		return reason;
	}

	public ConsoleUser getRegisteredBy() {
		return registeredBy;
	}

	public Instant getRegisteredAt() {
		return registeredAt;
	}

}
