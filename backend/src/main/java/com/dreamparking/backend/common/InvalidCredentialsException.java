package com.dreamparking.backend.common;

/** Wrong email or password, or an account that cannot sign in. The message never says which. Answered with 401. */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Correo o contraseña incorrectos");
	}

}
