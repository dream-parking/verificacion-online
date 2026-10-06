package com.dreamparking.backend.onboarding.entity.enums;

/** Lifecycle status of an onboarding request. Same values as the PostgreSQL enum {@code request_status}. */
public enum RequestStatus {

	IN_PROGRESS,
	COMPLETED,
	ABANDONED

}
