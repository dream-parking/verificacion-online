package com.dreamparking.backend.account.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.account.dto.ChangeProfileToleranceRequest;
import com.dreamparking.backend.account.dto.ProfileToleranceResponse;
import com.dreamparking.backend.account.service.ProfileToleranceService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** Tolerance of the declared profile tracking and the log of its changes (VDI-137). Administrators only. */
@RestController
@RequestMapping("/api/console/admin/profile-tolerance")
@Tag(name = "Consola · Administración del perfil transaccional",
		description = "Tolerancia de desviación del perfil declarado (administradores)")
@SecurityRequirement(name = "bearerAuth")
public class ProfileToleranceAdminController {

	private final ProfileToleranceService toleranceService;

	public ProfileToleranceAdminController(ProfileToleranceService toleranceService) {
		this.toleranceService = toleranceService;
	}

	@ApiResponse(responseCode = "200", description = "Tolerancia vigente y bitácora de cambios")
	@Operation(summary = "Tolerancia vigente y bitácora de cambios",
			description = "Cada cambio trae el valor anterior y el nuevo, quién lo hizo (`changedBy`, vacío en el valor "
					+ "inicial) y cuándo (`changedAt`), del más reciente al más antiguo.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@GetMapping
	public ProfileToleranceResponse log() {
		return toleranceService.log();
	}

	@ApiResponse(responseCode = "200", description = "Tolerancia cambiada; devuelve la vigente y la bitácora")
	@Operation(summary = "Cambia la tolerancia de desviación del perfil declarado",
			description = "Aplica desde el siguiente movimiento que reporte el core, sin desplegar. Las desviaciones ya "
					+ "registradas conservan la tolerancia con la que se detectaron. Debe ser un entero de 0 a 45.")
	@ApiResponse(responseCode = "400", description = "Fuera de rango, vacía o igual a la vigente: `errors` dice por qué, "
			+ "en español, y no se guarda nada", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@PutMapping
	public ProfileToleranceResponse change(@Valid @RequestBody ChangeProfileToleranceRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return toleranceService.change(body, admin.id());
	}

}
