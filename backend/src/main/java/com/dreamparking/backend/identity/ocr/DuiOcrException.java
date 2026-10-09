package com.dreamparking.backend.identity.ocr;

/** The DUI could not be read because of the provider (off, failing, timing out); the message is stored with the document. */
public class DuiOcrException extends Exception {

	public DuiOcrException(String message) {
		super(message);
	}

	public DuiOcrException(String message, Throwable cause) {
		super(message, cause);
	}

}
