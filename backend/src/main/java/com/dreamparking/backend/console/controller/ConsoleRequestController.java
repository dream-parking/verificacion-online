package com.dreamparking.backend.console.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.common.dto.PageResponse;
import com.dreamparking.backend.console.dto.ConsoleRequestDetail;
import com.dreamparking.backend.console.dto.ConsoleRequestListItem;
import com.dreamparking.backend.console.service.ConsoleRequestService;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;

@RestController
@RequestMapping("/api/console/requests")
@Tag(name = "Consola · Solicitudes", description = "Listado y detalle de solicitudes para la consola administrativa")
@SecurityRequirement(name = "bearerAuth")
public class ConsoleRequestController {

	private final ConsoleRequestService service;

	public ConsoleRequestController(ConsoleRequestService service) {
		this.service = service;
	}

	@Operation(summary = "Listado de solicitudes, de la más reciente a la más antigua",
			description = "Filtra por estado, nivel de riesgo y texto (nombre o número). `page` empieza en 0; `size` máximo 100.")
	@GetMapping
	public PageResponse<ConsoleRequestListItem> list(@RequestParam(required = false) RequestStatus status,
			@RequestParam(required = false) RiskLevel riskLevel, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(status, riskLevel, q, page, size);
	}

	@ApiResponse(responseCode = "200", description = "Detalle de la solicitud")
	@Operation(summary = "Detalle de una solicitud",
			description = "Datos declarados, ingresos, movimiento esperado, score y regla aplicada, señales, tiempo por paso y línea de tiempo.")
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@GetMapping("/{requestId}")
	public ConsoleRequestDetail detail(@PathVariable UUID requestId) {
		return service.detail(requestId);
	}

}
