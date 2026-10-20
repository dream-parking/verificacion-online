package com.dreamparking.backend.rnpn.client;

/** The RNPN did not answer: it is off, failed or timed out. A result that does not match is not an error. */
public class RnpnException extends Exception {

	public RnpnException(String message) {
		super(message);
	}

	public RnpnException(String message, Throwable cause) {
		super(message, cause);
	}

}
