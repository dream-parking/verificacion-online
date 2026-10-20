package com.dreamparking.backend.risk.entity;

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

import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.risk.entity.enums.RiskDecisionType;

/**
 * Entry of the audit log of risk decisions: what was decided about a submitted request, why, and when. It is never
 * changed or deleted (the database refuses it), and it copies the score, the threshold and the model version so it can
 * be explained without the model.
 */
@Entity
@Table(name = "risk_decision_log")
public class RiskDecision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assessment_id", nullable = false, updatable = false)
	private RiskAssessment assessment;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false, length = 20, updatable = false)
	private RiskDecisionType decision;

	@Column(name = "reason", nullable = false, columnDefinition = "text", updatable = false)
	private String reason;

	@Column(name = "score", nullable = false, updatable = false)
	private Short score;

	@Column(name = "threshold", nullable = false, updatable = false)
	private Short threshold;

	@Column(name = "model_version", nullable = false, updatable = false)
	private Short modelVersion;

	@Column(name = "decided_by", nullable = false, length = 60, updatable = false)
	private String decidedBy = "SYSTEM";

	@CreationTimestamp
	@Column(name = "decided_at", nullable = false, updatable = false)
	private Instant decidedAt;

	protected RiskDecision() {
	}

	public RiskDecision(OnboardingRequest request, RiskAssessment assessment, RiskDecisionType decision, String reason,
			short threshold) {
		this.request = request;
		this.assessment = assessment;
		this.decision = decision;
		this.reason = reason;
		this.score = assessment.getScore();
		this.threshold = threshold;
		this.modelVersion = assessment.getModelVersion();
	}

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public RiskAssessment getAssessment() {
		return assessment;
	}

	public RiskDecisionType getDecision() {
		return decision;
	}

	public String getReason() {
		return reason;
	}

	public Short getScore() {
		return score;
	}

	public Short getThreshold() {
		return threshold;
	}

	public Short getModelVersion() {
		return modelVersion;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

}
