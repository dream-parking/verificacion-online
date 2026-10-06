package com.dreamparking.backend.console;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/console/users")
@Tag(name = "Consola · Usuarios", description = "Usuarios de la consola administrativa")
@SecurityRequirement(name = "bearerAuth")
public class ConsoleUserController {

	private final ConsoleUserService service;

	public ConsoleUserController(ConsoleUserService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "200", description = "Usuarios de la consola")
	@Operation(summary = "Usuarios de la consola",
			description = "Cualquier usuario autenticado ve los activos (para asignar alertas). Con `includeInactive=true`, solo administradores.")
	@ApiResponse(responseCode = "403", description = "`includeInactive` sin ser administrador", content = @Content)
	@GetMapping
	public List<ConsoleUserResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser caller) {
		if (includeInactive && !caller.isAdmin()) {
			throw new AccessDeniedException("Only administrators can list inactive users");
		}
		return service.list(includeInactive);
	}

	@Operation(summary = "Crea un usuario (administradores)",
			description = "Define el correo, el rol y una contraseña inicial que la persona debe cambiar al entrar.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o contraseña que no cumple la política", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese correo", content = @Content)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ConsoleUserResponse create(@Valid @RequestBody CreateUserRequest body) {
		return service.create(body);
	}

	@ApiResponse(responseCode = "200", description = "Usuario actualizado")
	@Operation(summary = "Edita nombre, cargo, rol o estado (administradores)",
			description = "Desactivar a alguien le quita el acceso de inmediato. No se puede quitar el último administrador activo.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "El usuario no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "Dejaría al sistema sin administrador activo", content = @Content)
	@PutMapping("/{userId}")
	public ConsoleUserResponse update(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest body) {
		return service.update(userId, body);
	}

	@Operation(summary = "Define una contraseña nueva para otro usuario (administradores)")
	@ApiResponse(responseCode = "400", description = "La contraseña no cumple la política", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "El usuario no existe", content = @Content)
	@PostMapping("/{userId}/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@PathVariable UUID userId, @Valid @RequestBody ResetPasswordRequest body) {
		service.resetPassword(userId, body.newPassword());
	}

}
