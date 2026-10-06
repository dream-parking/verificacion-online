package com.dreamparking.backend.risk;

import com.dreamparking.backend.common.PgEnumJdbcType;
import com.dreamparking.backend.console.ConsoleUser;
import com.dreamparking.backend.onboarding.OnboardingRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;

/** Result of evaluating a request's risk; only one assessment per request is current. */
@Entity
@Table(name = "evaluacion_riesgo")
public class RiskAssessment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitud_id", nullable = false)
	private OnboardingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "regla_id")
	private ScoreRule rule;

	@Convert(converter = RiskLevel.JpaConverter.class)
	@JdbcType(PgEnumJdbcType.class)
	@Column(name = "nivel", nullable = false, columnDefinition = "nivel_riesgo")
	private RiskLevel level;

	@Column(name = "valor_evaluado", precision = 14, scale = 2)
	private BigDecimal evaluatedValue;

	@Column(name = "explicacion", nullable = false, columnDefinition = "text")
	private String explanation;

	@CreationTimestamp
	@Column(name = "evaluado_en", nullable = false)
	private Instant evaluatedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "evaluado_por")
	private ConsoleUser evaluatedBy;

	@Column(name = "es_vigente", nullable = false)
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
