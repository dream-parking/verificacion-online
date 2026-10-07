package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;

/** The operation is not allowed in the resource's current state. Answered with 409. */
public class InvalidStateException extends ApiException {

	public InvalidStateException(String message) {
		super(HttpStatus.CONFLICT, message);
	}

}
