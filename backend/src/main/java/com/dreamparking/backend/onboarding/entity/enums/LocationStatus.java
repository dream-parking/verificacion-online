package com.dreamparking.backend.onboarding.entity.enums;

/**
 * Whether the app could capture the applicant's approximate location. Same values as the PostgreSQL enum
 * {@code location_status}. The console shows {@code PERMISSION_DENIED} and {@code UNAVAILABLE} as "not available".
 */
public enum LocationStatus {

	/** Latitude and longitude were captured. */
	AVAILABLE,

	/** The applicant did not grant the location permission. */
	PERMISSION_DENIED,

	/** Permission granted, but the phone could not get a position (location off, error or timeout). */
	UNAVAILABLE

}
