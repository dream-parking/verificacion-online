package com.dreamparking.backend.alert.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.alert.dto.AlertCommentRequest;
import com.dreamparking.backend.alert.dto.AlertDetailResponse;
import com.dreamparking.backend.alert.dto.AlertHistoryResponse;
import com.dreamparking.backend.alert.dto.AlertResponse;
import com.dreamparking.backend.alert.dto.AlertTypeResponse;
import com.dreamparking.backend.alert.dto.CloseAlertRequest;
import com.dreamparking.backend.alert.dto.RaiseAlertRequest;
import com.dreamparking.backend.alert.entity.enums.AlertStatus;
import com.dreamparking.backend.alert.service.AlertService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** Analyst inbox and alert workflow. Every action is done as the console user of the session. */
@RestController
@RequestMapping("/api/console")
@Tag(name = "Consola · Alertas", description = "Bandeja de trabajo del analista de fraude")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

	private final AlertService service;

	public AlertController(AlertService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "200", description = "Alertas abiertas")
	@Operation(summary = "Alertas abiertas ordenadas por criticidad",
			description = "De la más crítica a la menos crítica y, dentro de cada criticidad, la más reciente primero. "
					+ "`account` busca por los últimos 4 dígitos; `from` y `to` son días (aaaa-mm-dd, hora de El Salvador), ambos inclusivos.")
	@ApiResponse(responseCode = "400", description = "Filtro inválido", content = @Content)
	@GetMapping("/alerts")
	public List<AlertResponse> list(@RequestParam(required = false) AlertStatus status,
			@RequestParam(required = false) UUID assigneeId,
			@RequestParam(required = false) @Size(max = 30) @Pattern(regexp = "[0-9 *-]*") String account,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.inbox(status, assigneeId, account, from, to);
	}

	@ApiResponse(responseCode = "200", description = "Tipos de alerta")
	@Operation(summary = "Tipos de alerta y su criticidad por defecto")
	@GetMapping("/alert-types")
	public List<AlertTypeResponse> types() {
		return service.types();
	}

	@ApiResponse(responseCode = "200", description = "Alerta")
	@Operation(summary = "Detalle de una alerta", description = "Cuenta, tipo, evidencia, responsable y fechas del flujo.")
	@ApiResponse(responseCode = "404", description = "La alerta no existe", content = @Content)
	@GetMapping("/alerts/{alertId}")
	public AlertDetailResponse get(@PathVariable UUID alertId) {
		return service.get(alertId);
	}

	@ApiResponse(responseCode = "200", description = "Cambios de estado, del más antiguo al más reciente")
	@Operation(summary = "Historial de una alerta")
	@ApiResponse(responseCode = "404", description = "La alerta no existe", content = @Content)
	@GetMapping("/alerts/{alertId}/history")
	public List<AlertHistoryResponse> history(@PathVariable UUID alertId) {
		return service.history(alertId);
	}

	@ApiResponse(responseCode = "201", description = "Alerta creada sin responsable")
	@Operation(summary = "Levanta una alerta sobre una cuenta",
			description = "Sin `criticality` se usa la del tipo de alerta.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o tipo de alerta desconocido", content = @Content)
	@ApiResponse(responseCode = "404", description = "La cuenta no existe", content = @Content)
	@PostMapping("/alerts")
	@ResponseStatus(HttpStatus.CREATED)
	public AlertDetailResponse raise(@Valid @RequestBody RaiseAlertRequest body) {
		return service.raise(body);
	}

	@ApiResponse(responseCode = "200", description = "La alerta quedó asignada al usuario de la sesión")
	@Operation(summary = "Tomar una alerta sin dueño",
			description = "Se la asigna al usuario de la sesión y queda en el historial de la alerta. Solo analistas de fraude y administradores.")
	@ApiResponse(responseCode = "403", description = "El rol del usuario no puede tomar alertas", content = @Content)
	@ApiResponse(responseCode = "404", description = "La alerta no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La alerta ya fue tomada por alguien más o está cerrada", content = @Content)
	@PostMapping("/alerts/{alertId}/take")
	public AlertResponse take(@PathVariable UUID alertId,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return service.take(alertId, user.id(), null);
	}

	@ApiResponse(responseCode = "200", description = "La alerta quedó en revisión")
	@Operation(summary = "Empieza a revisar una alerta", description = "Solo quien la tomó.")
	@ApiResponse(responseCode = "403", description = "El usuario no es el responsable de la alerta", content = @Content)
	@ApiResponse(responseCode = "404", description = "La alerta no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La alerta no está asignada", content = @Content)
	@PostMapping("/alerts/{alertId}/review")
	public AlertDetailResponse startReview(@PathVariable UUID alertId,
			@Valid @RequestBody(required = false) AlertCommentRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return service.startReview(alertId, user.id(), body == null ? null : body.comment());
	}

	@ApiResponse(responseCode = "200", description = "Alerta cerrada")
	@Operation(summary = "Cierra una alerta con su resolución",
			description = "Puede cerrarla quien la tomó, la líder de Conozca a su Cliente o un administrador.")
	@ApiResponse(responseCode = "400", description = "Falta la resolución", content = @Content)
	@ApiResponse(responseCode = "403", description = "El usuario no puede cerrar esta alerta", content = @Content)
	@ApiResponse(responseCode = "404", description = "La alerta no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La alerta no está asignada ni en revisión", content = @Content)
	@PostMapping("/alerts/{alertId}/close")
	public AlertDetailResponse close(@PathVariable UUID alertId, @Valid @RequestBody CloseAlertRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return service.close(alertId, user.id(), body);
	}

}
