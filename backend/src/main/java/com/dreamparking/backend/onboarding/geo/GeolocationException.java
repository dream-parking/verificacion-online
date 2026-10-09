package com.dreamparking.backend.onboarding.geo;

/** The IP could not be located; the message says why and is stored with the session. */
public class GeolocationException extends Exception {

	public GeolocationException(String message) {
		super(message);
	}

	public GeolocationException(String message, Throwable cause) {
		super(message, cause);
	}

}
