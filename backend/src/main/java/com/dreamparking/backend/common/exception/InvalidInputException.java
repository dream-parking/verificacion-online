package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Input is well formed but not acceptable (e.g. an unknown catalog code). Answered with 400. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInputException extends RuntimeException {

	public InvalidInputException(String message) {
		super(message);
	}

}
