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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.dreamparking.backend.console.entity.ConsoleUser;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.entity.enums.RuleStatus;

/** Versioned score rule; only one version per code can be in force at a time. */
@Entity
@Table(name = "score_rule")
public class ScoreRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "code", nullable = false, length = 10)
	private String code;

	@Column(name = "rule_version", nullable = false)
	private Short ruleVersion = (short) 1;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description", nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "evaluated_field", nullable = false, length = 60)
	private String evaluatedField;

	@Column(name = "operator", nullable = false, length = 4)
	private String operator;

	@Column(name = "threshold", nullable = false, precision = 14, scale = 2)
	private BigDecimal threshold;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "USD";

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "result_if_matched", nullable = false, columnDefinition = "risk_level")
	private RiskLevel resultIfMatched;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "result_if_not_matched", nullable = false, columnDefinition = "risk_level")
	private RiskLevel resultIfNotMatched;

	@Column(name = "priority", nullable = false)
	private Short priority = (short) 100;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "status", nullable = false, columnDefinition = "rule_status")
	private RuleStatus status = RuleStatus.DRAFT;

	@Column(name = "status_note", length = 250)
	private String statusNote;

	@Column(name = "valid_from", nullable = false)
	private Instant validFrom;

	@Column(name = "valid_to")
	private Instant validTo;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by")
	private ConsoleUser updatedBy;

	public Integer getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Short getRuleVersion() {
		return ruleVersion;
	}

	public void setRuleVersion(Short ruleVersion) {
		this.ruleVersion = ruleVersion;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getEvaluatedField() {
		return evaluatedField;
	}

	public void setEvaluatedField(String evaluatedField) {
		this.evaluatedField = evaluatedField;
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
	}

	public BigDecimal getThreshold() {
		return threshold;
	}

	public void setThreshold(BigDecimal threshold) {
		this.threshold = threshold;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public RiskLevel getResultIfMatched() {
		return resultIfMatched;
	}

	public void setResultIfMatched(RiskLevel resultIfMatched) {
		this.resultIfMatched = resultIfMatched;
	}

	public RiskLevel getResultIfNotMatched() {
		return resultIfNotMatched;
	}

	public void setResultIfNotMatched(RiskLevel resultIfNotMatched) {
		this.resultIfNotMatched = resultIfNotMatched;
	}

	public Short getPriority() {
		return priority;
	}

	public void setPriority(Short priority) {
		this.priority = priority;
	}

	public RuleStatus getStatus() {
		return status;
	}

	public void setStatus(RuleStatus status) {
		this.status = status;
	}

	public String getStatusNote() {
		return statusNote;
	}

	public void setStatusNote(String statusNote) {
		this.statusNote = statusNote;
	}

	public Instant getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(Instant validFrom) {
		this.validFrom = validFrom;
	}

	public Instant getValidTo() {
		return validTo;
	}

	public void setValidTo(Instant validTo) {
		this.validTo = validTo;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public ConsoleUser getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(ConsoleUser updatedBy) {
		this.updatedBy = updatedBy;
	}

}
