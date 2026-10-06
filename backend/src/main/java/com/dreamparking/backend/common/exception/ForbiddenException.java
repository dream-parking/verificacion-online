package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;

/** The user may not perform this operation. Answered with 403. */
public class ForbiddenException extends ApiException {

	public ForbiddenException(String message) {
		super(HttpStatus.FORBIDDEN, message);
	}

}
