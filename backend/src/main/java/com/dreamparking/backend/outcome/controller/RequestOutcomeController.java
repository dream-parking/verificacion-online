package com.dreamparking.backend.outcome.controller;

import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.outcome.dto.RegisterOutcomeRequest;
import com.dreamparking.backend.outcome.dto.RequestOutcomeResponse;
import com.dreamparking.backend.outcome.service.RequestOutcomeService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** How a case ended: fraud confirmed or false positive, and the correction of that outcome. */
@RestController
@RequestMapping("/api/console/requests/{requestId}/outcome")
@Tag(name = "Consola · Desenlace de casos",
		description = "Cómo terminó un caso: fraude confirmado (alimenta las listas internas de riesgo) o falso positivo (solo se registra)")
@SecurityRequirement(name = "bearerAuth")
public class RequestOutcomeController {

	private final RequestOutcomeService service;

	public RequestOutcomeController(RequestOutcomeService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "201", description = "Desenlace registrado")
	@Operation(summary = "Registra el desenlace de una solicitud",
			description = "`FRAUDE_CONFIRMADO` pone el DUI, el teléfono y el dispositivo de la solicitud en la lista de fraude confirmado "
					+ "(lo que ya esté en la lista no se repite). `FALSO_POSITIVO` solo lo registra. Registrar otro desenlace corrige el "
					+ "anterior: no lo edita, agrega una fila, y un falso positivo saca de las listas lo que los desenlaces anteriores "
					+ "de la solicitud habían puesto. Solo analistas de fraude y administradores.")
	@ApiResponse(responseCode = "400", description = "Falta el desenlace o el motivo, o no tienen el formato", content = @Content)
	@ApiResponse(responseCode = "403", description = "Solo analistas de fraude y administradores", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RequestOutcomeResponse register(@PathVariable UUID requestId, @Valid @RequestBody RegisterOutcomeRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return service.register(requestId, body, user.id());
	}

	@ApiResponse(responseCode = "200", description = "Desenlaces, del más antiguo al más reciente; el último es el vigente")
	@Operation(summary = "Desenlaces de una solicitud",
			description = "Cada desenlace con quién, cuándo, el motivo y los datos que puso o sacó de las listas.")
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@GetMapping
	public List<RequestOutcomeResponse> history(@PathVariable UUID requestId) {
		return service.history(requestId);
	}

}
