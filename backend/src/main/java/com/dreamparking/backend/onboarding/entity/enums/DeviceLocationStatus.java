package com.dreamparking.backend.onboarding.entity.enums;

/** Whether the phone reported the city of the request, with the customer's permission (VDI-73). */
public enum DeviceLocationStatus {

	/** Permission granted and the phone found the city. */
	AVAILABLE,

	/** The customer declined to share it, in the app or in the system permission. */
	DENIED,

	/** Permission granted, but the phone could not find it (location turned off, timeout...). */
	UNAVAILABLE

}
