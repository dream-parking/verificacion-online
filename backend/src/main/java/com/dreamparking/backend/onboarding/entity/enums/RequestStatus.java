package com.dreamparking.backend.onboarding.entity.enums;

/** Lifecycle status of an onboarding request. Same values as the PostgreSQL enum {@code request_status}. */
public enum RequestStatus {

	IN_PROGRESS,
	COMPLETED,

	/** Submitted, but its score went above the threshold: it is not approved automatically (VDI-117). */
	ENHANCED_REVIEW,

	/** Submitted, but the RNPN did not answer after the retries: it is not approved automatically (VDI-111). */
	AWAITING_RNPN,

	/** Held for enhanced review and sent by an analyst to a video call or to a branch (VDI-143). */
	ESCALATED,

	ABANDONED

}
