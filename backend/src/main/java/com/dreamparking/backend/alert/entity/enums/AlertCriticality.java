package com.dreamparking.backend.alert.entity.enums;

/** Criticality of an alert, from most to least severe. Same values as the PostgreSQL enum {@code alert_criticality}. */
public enum AlertCriticality {

	CRITICAL,
	HIGH,
	MEDIUM,
	LOW

}
