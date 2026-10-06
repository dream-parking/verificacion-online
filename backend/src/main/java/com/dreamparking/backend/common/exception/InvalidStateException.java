package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The operation is not allowed in the resource's current state. Answered with 409. */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidStateException extends RuntimeException {

	public InvalidStateException(String message) {
		super(message);
	}

}
