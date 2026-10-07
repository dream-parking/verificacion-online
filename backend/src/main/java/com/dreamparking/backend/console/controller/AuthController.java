package com.dreamparking.backend.console.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.console.dto.ChangePasswordRequest;
import com.dreamparking.backend.console.dto.ConsoleUserResponse;
import com.dreamparking.backend.console.dto.LoginRequest;
import com.dreamparking.backend.console.dto.LoginResponse;
import com.dreamparking.backend.console.service.AuthService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/console/auth")
@Tag(name = "Consola · Acceso", description = "Inicio de sesión con correo y contraseña")
public class AuthController {

	private final AuthService authService;

	private final ConsoleUserService userService;

	public AuthController(AuthService authService, ConsoleUserService userService) {
		this.authService = authService;
		this.userService = userService;
	}

	@ApiResponse(responseCode = "200", description = "Sesión iniciada")
	@Operation(summary = "Inicia sesión",
			description = "Devuelve el token que se envía como `Authorization: Bearer <accessToken>`. "
					+ "Tras 5 intentos fallidos seguidos el correo se bloquea 15 minutos (429).")
	@ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos (también si el usuario está inactivo o sin contraseña)", content = @Content)
	@ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; ver `Retry-After`", content = @Content)
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest body) {
		return authService.login(body);
	}

	@Operation(summary = "Usuario de la sesión actual", security = @SecurityRequirement(name = "bearerAuth"))
	@GetMapping("/me")
	public ConsoleUserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return userService.get(user.id());
	}

	@Operation(summary = "Cambia la contraseña propia", security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponse(responseCode = "400", description = "La actual no es correcta o la nueva no cumple la política", content = @Content)
	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ChangePasswordRequest body) {
		userService.changeOwnPassword(user.id(), body.currentPassword(), body.newPassword());
	}

}
