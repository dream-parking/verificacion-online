package com.dreamparking.backend.alert.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.alert.dto.AlertTypeResponse;
import com.dreamparking.backend.alert.dto.CreateAlertTypeRequest;
import com.dreamparking.backend.alert.dto.UpdateAlertTypeRequest;
import com.dreamparking.backend.alert.service.AlertTypeService;
import com.dreamparking.backend.common.validation.Code;
import com.dreamparking.backend.security.AuthenticatedUser;

/** Maintenance of the alert types. Administrators only; any console user reads them in GET /api/console/alert-types. */
@RestController
@RequestMapping("/api/console/admin/alert-types")
@Tag(name = "Consola · Administración de alertas", description = "Tipos de alerta (administradores)")
@SecurityRequirement(name = "bearerAuth")
public class AlertTypeAdminController {

	private final AlertTypeService alertTypeService;

	public AlertTypeAdminController(AlertTypeService alertTypeService) {
		this.alertTypeService = alertTypeService;
	}

	@ApiResponse(responseCode = "201", description = "Tipo de alerta creado")
	@Operation(summary = "Crea un tipo de alerta")
	@ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "409", description = "El código ya existe", content = @Content)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AlertTypeResponse create(@Valid @RequestBody CreateAlertTypeRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return alertTypeService.create(body, admin.id());
	}

	@ApiResponse(responseCode = "200", description = "Tipo de alerta actualizado")
	@Operation(summary = "Edita la descripción o la criticidad por defecto de un tipo de alerta",
			description = "Las alertas ya levantadas conservan su propia criticidad.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "El tipo de alerta no existe", content = @Content)
	@PutMapping("/{code}")
	public AlertTypeResponse update(@PathVariable @Size(max = 40) @Code String code,
			@Valid @RequestBody UpdateAlertTypeRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return alertTypeService.update(code, body, admin.id());
	}

}
