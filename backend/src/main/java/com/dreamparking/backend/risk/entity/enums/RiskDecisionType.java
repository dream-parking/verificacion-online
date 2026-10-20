package com.dreamparking.backend.risk.entity.enums;

/** What was decided about a submitted request after scoring it. Same values as the {@code decision} check of {@code risk_decision_log}. */
public enum RiskDecisionType {

	/** The score does not go above the threshold: the request continues in the automatic flow. */
	AUTOMATIC_FLOW,

	/** The score goes above the threshold: the request is not approved automatically and waits for enhanced review. */
	ENHANCED_REVIEW

}
