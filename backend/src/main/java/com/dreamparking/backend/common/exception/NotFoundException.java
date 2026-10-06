package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;

/** The requested resource does not exist. Answered with 404. */
public class NotFoundException extends ApiException {

	public NotFoundException(String message) {
		super(HttpStatus.NOT_FOUND, message);
	}

}
