package com.dreamparking.backend.onboarding.entity.enums;

/** Lifecycle status of an onboarding request. Same values as the PostgreSQL enum {@code request_status}. */
public enum RequestStatus {

	IN_PROGRESS,
	COMPLETED,

	/** Submitted, but its score went above the threshold: it is not approved automatically (VDI-117). */
	ENHANCED_REVIEW,

	ABANDONED

}
