package com.dreamparking.backend.alert;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/alerts")
@Tag(name = "Consola · Alertas", description = "Bandeja de trabajo del analista de fraude")
public class AlertController {

	private final AlertService service;

	public AlertController(AlertService service) {
		this.service = service;
	}

	@Operation(summary = "Alertas abiertas ordenadas por criticidad",
			description = "De la más crítica a la menos crítica y, dentro de cada criticidad, la más reciente primero. "
					+ "`account` busca por los últimos 4 dígitos; `from` y `to` son días (aaaa-mm-dd, hora de El Salvador), ambos inclusivos.")
	@ApiResponse(responseCode = "400", description = "Filtro inválido", content = @Content)
	@GetMapping
	public List<AlertResponse> list(@RequestParam(required = false) AlertStatus status,
			@RequestParam(required = false) UUID assigneeId, @RequestParam(required = false) String account,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.list(status, assigneeId, account, from, to);
	}

	@Operation(summary = "Tomar una alerta sin dueño",
			description = "Se la asigna al usuario indicado y queda en el historial de la alerta.")
	@ApiResponse(responseCode = "400", description = "Usuario de la consola desconocido o inactivo", content = @Content)
	@ApiResponse(responseCode = "404", description = "La alerta no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La alerta ya fue tomada por alguien más o está cerrada", content = @Content)
	@PostMapping("/{alertId}/take")
	public AlertResponse take(@PathVariable UUID alertId, @Valid @RequestBody TakeAlertRequest body) {
		return service.take(alertId, body.userId());
	}

}
