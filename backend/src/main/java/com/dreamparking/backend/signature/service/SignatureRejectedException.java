package com.dreamparking.backend.signature.service;

import org.springframework.http.HttpStatus;

import com.dreamparking.backend.common.exception.ApiException;

/**
 * The signature service rejected the signature (VDI-130). Answered with 422: nothing is stored as a signature and the
 * customer can try again.
 */
public class SignatureRejectedException extends ApiException {

	public SignatureRejectedException(String reason) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "The signature was rejected: " + reason);
	}

}
