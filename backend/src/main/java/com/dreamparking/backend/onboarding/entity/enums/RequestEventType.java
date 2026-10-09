package com.dreamparking.backend.onboarding.entity.enums;

/** Kinds of entries in an onboarding request timeline. Same values as the PostgreSQL enum {@code request_event_type}. */
public enum RequestEventType {

	REQUEST_STARTED,
	PRIVACY_ACCEPTED,
	BASIC_DATA_COMPLETED,
	IDENTITY_DOCUMENT_CAPTURED,
	IDENTITY_DOCUMENT_CONFIRMED,
	INCOME_REGISTERED,
	EXPECTED_ACTIVITY_REGISTERED,
	REQUEST_SUBMITTED,
	SUBMISSION_FAILED,
	SCORE_ASSIGNED,
	REQUEST_ABANDONED

}
