package com.dreamparking.backend.liveness.client;

/** The selfie could not be checked because of the provider (off, failing, timing out); the message is stored with the check. */
public class LivenessException extends Exception {

	public LivenessException(String message) {
		super(message);
	}

	public LivenessException(String message, Throwable cause) {
		super(message, cause);
	}

}
