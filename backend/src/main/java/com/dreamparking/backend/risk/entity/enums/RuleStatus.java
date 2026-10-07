package com.dreamparking.backend.risk.entity.enums;

/** Approval status of a score rule version. Same values as the PostgreSQL enum {@code rule_status}. */
public enum RuleStatus {

	DRAFT,
	PROVISIONAL,
	CONFIRMED,
	RETIRED

}
