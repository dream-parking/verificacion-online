package com.dreamparking.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** 401 and 403 answered in the same {@code application/problem+json} shape as the rest of the API. */
final class ProblemJsonHandlers {

	static final AuthenticationEntryPoint UNAUTHORIZED = (request, response, ex) -> write(response,
			HttpStatus.UNAUTHORIZED, "Necesitas iniciar sesión para usar este recurso.", "Bearer");

	static final AccessDeniedHandler FORBIDDEN = (request, response, ex) -> write(response, HttpStatus.FORBIDDEN,
			"Tu usuario no tiene permiso para esta acción.", null);

	private ProblemJsonHandlers() {
	}

	private static void write(HttpServletResponse response, HttpStatus status, String detail, String challenge)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		if (challenge != null) {
			response.setHeader("WWW-Authenticate", challenge);
		}
		// Fixed strings only, so there is nothing to escape.
		response.getOutputStream()
			.write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
				.formatted(status.getReasonPhrase(), status.value(), detail)
				.getBytes(StandardCharsets.UTF_8));
	}

}
