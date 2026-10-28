package com.dreamparking.backend.risklist.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.common.validation.Code;
import com.dreamparking.backend.risklist.dto.CreateRiskListEntryRequest;
import com.dreamparking.backend.risklist.dto.DeactivateRiskListEntryRequest;
import com.dreamparking.backend.risklist.dto.RiskListEntryChangeResponse;
import com.dreamparking.backend.risklist.dto.RiskListEntryResponse;
import com.dreamparking.backend.risklist.dto.RiskListResponse;
import com.dreamparking.backend.risklist.service.RiskListAdminService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** The internal risk lists: adding data to them, taking it off and reading the history. Administrators only. */
@RestController
@RequestMapping("/api/console/admin/risk-lists")
@Tag(name = "Consola · Administración de listas internas de riesgo",
		description = "Listas de datos de casos de fraude ya conocidos (administradores). Los valores se guardan solo como huella y no se devuelven")
@SecurityRequirement(name = "bearerAuth")
public class RiskListAdminController {

	private final RiskListAdminService riskListAdminService;

	public RiskListAdminController(RiskListAdminService riskListAdminService) {
		this.riskListAdminService = riskListAdminService;
	}

	@ApiResponse(responseCode = "200", description = "Listas, de mayor a menor peso")
	@Operation(summary = "Listas internas de riesgo", description = "Cada lista tiene un tipo y el peso que sus coincidencias llevan al score.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@GetMapping
	public List<RiskListResponse> lists() {
		return riskListAdminService.lists();
	}

	@ApiResponse(responseCode = "200", description = "Entradas, de la más reciente a la más antigua")
	@Operation(summary = "Entradas de una lista, activas e inactivas",
			description = "No trae el dato ni su huella: solo el tipo de dato, el motivo, quién y cuándo lo agregó y, si ya salió, quién y cuándo.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "La lista no existe", content = @Content)
	@GetMapping("/{code}/entries")
	public List<RiskListEntryResponse> entries(@PathVariable @Size(max = 30) @Code String code) {
		return riskListAdminService.entries(code);
	}

	@ApiResponse(responseCode = "201", description = "Entrada agregada")
	@Operation(summary = "Da de alta un dato en una lista",
			description = "El dato (DUI, teléfono celular o huella del dispositivo) se guarda solo como huella con sal; el nombre no interviene. "
					+ "La alta queda en la bitácora con quién, cuándo y el motivo.")
	@ApiResponse(responseCode = "400", description = "El dato no tiene el formato de su tipo", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "La lista no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "El dato ya está activo en esa lista", content = @Content)
	@PostMapping("/{code}/entries")
	@ResponseStatus(HttpStatus.CREATED)
	public RiskListEntryResponse add(@PathVariable @Size(max = 30) @Code String code,
			@Valid @RequestBody CreateRiskListEntryRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return riskListAdminService.add(code, body, admin.id());
	}

	@ApiResponse(responseCode = "200", description = "Entrada dada de baja")
	@Operation(summary = "Da de baja una entrada",
			description = "No la borra: la marca inactiva, así que deja de coincidir y queda en el historial con quién, cuándo y el motivo.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "La entrada no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La entrada ya está inactiva", content = @Content)
	@PostMapping("/entries/{entryId}/deactivate")
	public RiskListEntryResponse deactivate(@PathVariable Long entryId,
			@Valid @RequestBody DeactivateRiskListEntryRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser admin) {
		return riskListAdminService.deactivate(entryId, body, admin.id());
	}

	@ApiResponse(responseCode = "200", description = "Cambios, del más antiguo al más reciente")
	@Operation(summary = "Bitácora de una entrada", description = "Alta y baja, cada una con quién, cuándo y el motivo.")
	@ApiResponse(responseCode = "403", description = "Solo administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "La entrada no existe", content = @Content)
	@GetMapping("/entries/{entryId}/changes")
	public List<RiskListEntryChangeResponse> history(@PathVariable Long entryId) {
		return riskListAdminService.history(entryId);
	}

}
