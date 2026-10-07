package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Input is well formed but not acceptable (e.g. an unknown catalog code). Answered with 400. */
public class InvalidInputException extends ApiException {

	public InvalidInputException(String message) {
		super(HttpStatus.BAD_REQUEST, message);
	}

}
