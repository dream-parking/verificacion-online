package com.dreamparking.backend.risk.entity;

import java.math.BigDecimal;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.onboarding.entity.OnboardingRequest;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Result of evaluating a request's risk; only one assessment per request is current. */
@Entity
@Table(name = "risk_assessment")
public class RiskAssessment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rule_id")
	private ScoreRule rule;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "level", nullable = false, columnDefinition = "risk_level")
	private RiskLevel level;

	@Column(name = "evaluated_value", precision = 14, scale = 2)
	private BigDecimal evaluatedValue;

	@Column(name = "explanation", nullable = false, columnDefinition = "text")
	private String explanation;

	@CreationTimestamp
	@Column(name = "evaluated_at", nullable = false)
	private Instant evaluatedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "evaluated_by")
	private ConsoleUser evaluatedBy;

	@Column(name = "is_current", nullable = false)
	private Boolean current = true;

	public Long getId() {
		return id;
	}

	public OnboardingRequest getRequest() {
		return request;
	}

	public void setRequest(OnboardingRequest request) {
		this.request = request;
	}

	public ScoreRule getRule() {
		return rule;
	}

	public void setRule(ScoreRule rule) {
		this.rule = rule;
	}

	public RiskLevel getLevel() {
		return level;
	}

	public void setLevel(RiskLevel level) {
		this.level = level;
	}

	public BigDecimal getEvaluatedValue() {
		return evaluatedValue;
	}

	public void setEvaluatedValue(BigDecimal evaluatedValue) {
		this.evaluatedValue = evaluatedValue;
	}

	public String getExplanation() {
		return explanation;
	}

	public void setExplanation(String explanation) {
		this.explanation = explanation;
	}

	public Instant getEvaluatedAt() {
		return evaluatedAt;
	}

	public ConsoleUser getEvaluatedBy() {
		return evaluatedBy;
	}

	public void setEvaluatedBy(ConsoleUser evaluatedBy) {
		this.evaluatedBy = evaluatedBy;
	}

	public Boolean getCurrent() {
		return current;
	}

	public void setCurrent(Boolean current) {
		this.current = current;
	}

}
