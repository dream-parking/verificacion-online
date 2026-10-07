package com.dreamparking.backend.common.exception;

import org.springframework.http.HttpStatus;

/** Wrong email or password, or an account that cannot sign in. The message never says which. Answered with 401. */
public class InvalidCredentialsException extends ApiException {

	public InvalidCredentialsException() {
		super(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
	}

}
