package com.dreamparking.backend.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.dreamparking.backend.risk.entity.RiskAssessment;
import com.dreamparking.backend.risk.entity.ScoreRule;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/**
 * Risk score of a request and why it was assigned. The rule fields ({@code ruleCode}, {@code ruleName},
 * {@code ruleThreshold}) are null when no rule applied; the threshold is the one of the rule version that was applied.
 */
public record RiskAssessmentResponse(RiskLevel level, String ruleCode, String ruleName, BigDecimal ruleThreshold,
		BigDecimal evaluatedValue, String explanation, Instant evaluatedAt) {

	public static RiskAssessmentResponse of(RiskAssessment assessment) {
		ScoreRule rule = assessment.getRule();
		return new RiskAssessmentResponse(assessment.getLevel(), rule == null ? null : rule.getCode(),
				rule == null ? null : rule.getName(), rule == null ? null : rule.getThreshold(),
				assessment.getEvaluatedValue(), assessment.getExplanation(), assessment.getEvaluatedAt());
	}

}
