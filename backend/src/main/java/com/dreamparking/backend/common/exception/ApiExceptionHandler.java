package com.dreamparking.backend.common.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	/** Someone else changed the same record first (optimistic locking): the client should reload and retry. */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ProblemDetail concurrentModification() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The record was modified by someone else. Reload it and try again.");
	}

}
