package com.dreamparking.backend.onboarding.entity.enums;

/** Kinds of entries in an onboarding request timeline. Same values as the PostgreSQL enum {@code request_event_type}. */
public enum RequestEventType {

	REQUEST_STARTED,
	PRIVACY_ACCEPTED,
	BASIC_DATA_COMPLETED,
	IDENTITY_DOCUMENT_CAPTURED,
	IDENTITY_DOCUMENT_CONFIRMED,
	IDENTITY_DOCUMENT_REJECTED,
	LIVENESS_CHECKED,
	INCOME_REGISTERED,
	EXPECTED_ACTIVITY_REGISTERED,
	REQUEST_SUBMITTED,
	SUBMISSION_FAILED,
	SCORE_ASSIGNED,
	ENHANCED_REVIEW_REQUIRED,
	RNPN_CHECKED,
	/** An analyst escalated the request to a video call or to a branch (VDI-143). */
	REQUEST_ESCALATED,
	/** The customer signed the account contract (VDI-130). */
	CONTRACT_SIGNED,
	/** The signature service rejected the signature; the customer can try again (VDI-130). */
	CONTRACT_SIGNATURE_REJECTED,
	/**
	 * The customer chose a product (VDI-148). The code that wrote it is reverted, but the value stays in the database type
	 * (V41) and in the timeline of the requests that chose one, so it has to stay readable.
	 */
	PRODUCT_REQUESTED,
	REQUEST_ABANDONED

}
