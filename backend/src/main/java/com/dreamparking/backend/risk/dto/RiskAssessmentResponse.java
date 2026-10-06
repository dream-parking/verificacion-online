package com.dreamparking.backend.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.dreamparking.backend.risk.entity.RiskAssessment;
import com.dreamparking.backend.risk.entity.ScoreRule;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

/** Risk score of a request and why it was assigned. {@code ruleCode} is null when no rule applied. */
public record RiskAssessmentResponse(RiskLevel level, String ruleCode, BigDecimal evaluatedValue, String explanation,
		Instant evaluatedAt) {

	public static RiskAssessmentResponse of(RiskAssessment assessment) {
		ScoreRule rule = assessment.getRule();
		return new RiskAssessmentResponse(assessment.getLevel(), rule == null ? null : rule.getCode(),
				assessment.getEvaluatedValue(), assessment.getExplanation(), assessment.getEvaluatedAt());
	}

}
