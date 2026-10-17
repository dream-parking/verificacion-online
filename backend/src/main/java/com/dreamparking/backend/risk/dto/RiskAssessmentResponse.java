package com.dreamparking.backend.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dreamparking.backend.risk.entity.RiskAssessment;
import com.dreamparking.backend.risk.entity.RiskAssessmentSignal;
import com.dreamparking.backend.risk.entity.ScoreRule;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.risk.entity.enums.ScoreSignal;

/**
 * Risk score of a request and why it was assigned. {@code score} (0 to 100), {@code modelVersion} and
 * {@code signals} come from the score model; they are null / empty in assessments made by rule R-01 before it, which
 * fill the rule fields ({@code ruleCode}, {@code ruleName}, {@code ruleThreshold}, from the rule version applied)
 * instead.
 */
public record RiskAssessmentResponse(RiskLevel level, Integer score, Short modelVersion, List<SignalContribution> signals,
		String ruleCode, String ruleName, BigDecimal ruleThreshold, BigDecimal evaluatedValue, String explanation,
		Instant evaluatedAt) {

	/**
	 * What a signal added to the score. {@code points} is null when the signal was not available (it adds nothing)
	 * and {@code maxPoints} is null when the model gives it no weight yet.
	 */
	public record SignalContribution(ScoreSignal signal, String name, Integer points, Integer maxPoints,
			String observation) {

		static SignalContribution of(RiskAssessmentSignal row) {
			return new SignalContribution(row.getSignal(), row.getSignal().label(),
					row.getPoints() == null ? null : row.getPoints().intValue(),
					row.getMaxPoints() == null ? null : row.getMaxPoints().intValue(), row.getObservation());
		}

	}

	public static RiskAssessmentResponse of(RiskAssessment assessment) {
		ScoreRule rule = assessment.getRule();
		return new RiskAssessmentResponse(assessment.getLevel(),
				assessment.getScore() == null ? null : assessment.getScore().intValue(), assessment.getModelVersion(),
				assessment.getSignals().stream().map(SignalContribution::of).toList(),
				rule == null ? null : rule.getCode(), rule == null ? null : rule.getName(),
				rule == null ? null : rule.getThreshold(), assessment.getEvaluatedValue(), assessment.getExplanation(),
				assessment.getEvaluatedAt());
	}

}
