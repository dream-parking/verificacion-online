package com.dreamparking.backend.alert.entity.enums;

/** Workflow status of an alert in the console inbox. Same values as the PostgreSQL enum {@code alert_status}. */
public enum AlertStatus {

	UNASSIGNED,
	ASSIGNED,
	IN_REVIEW,
	CLOSED

}
