package com.dreamparking.backend.risk.entity.enums;

/** Risk score assigned to an onboarding request. Same values as the PostgreSQL enum {@code risk_level}. */
public enum RiskLevel {

	NOT_EVALUATED,
	LOW,
	PENDING_REVIEW,
	MEDIUM,
	HIGH

}
