package com.dreamparking.backend.risk;

import java.math.BigDecimal;
import java.time.Instant;

/** A score rule in force, as shown in the console's read-only rule view. */
public record ScoreRuleResponse(String code, short version, String name, String description, String evaluatedField,
		String operator, BigDecimal threshold, String currency, RiskLevel resultIfMatched,
		RiskLevel resultIfNotMatched, RuleStatus status, String statusNote, Instant validFrom) {

	static ScoreRuleResponse of(ScoreRule rule) {
		return new ScoreRuleResponse(rule.getCode(), rule.getRuleVersion(), rule.getName(), rule.getDescription(),
				rule.getEvaluatedField(), rule.getOperator(), rule.getThreshold(), rule.getCurrency(),
				rule.getResultIfMatched(), rule.getResultIfNotMatched(), rule.getStatus(), rule.getStatusNote(),
				rule.getValidFrom());
	}

}
