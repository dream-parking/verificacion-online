package com.dreamparking.backend.risk.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.risk.dto.EscalateRequest;
import com.dreamparking.backend.risk.dto.RiskDecisionResponse;
import com.dreamparking.backend.risk.service.EscalationService;
import com.dreamparking.backend.security.AuthenticatedUser;

/** Escalation of a request held for enhanced review to a video call or to a branch (VDI-143). */
@RestController
@RequestMapping("/api/console/requests/{requestId}/escalation")
@Tag(name = "Consola · Escalamiento",
		description = "El analista envía una solicitud en revisión reforzada a videollamada o a agencia")
@SecurityRequirement(name = "bearerAuth")
public class EscalationController {

	private final EscalationService service;

	public EscalationController(EscalationService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "201", description = "Solicitud escalada; devuelve la decisión registrada en la bitácora")
	@Operation(summary = "Escala una solicitud en revisión reforzada",
			description = "La solicitud pasa a `ESCALATED` con el canal elegido y conserva todo lo capturado. El escalamiento queda "
					+ "en la bitácora de decisiones (`ESCALATED`) con el canal, el motivo, quién lo decidió y la fecha y hora; la "
					+ "bitácora no se puede editar. La app ve el estado y el canal, nunca el score ni los criterios. Cualquier "
					+ "usuario de la consola.")
	@ApiResponse(responseCode = "400", description = "Falta el canal o el motivo, o no tienen el formato", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud no está en revisión reforzada", content = @Content)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RiskDecisionResponse escalate(@PathVariable UUID requestId, @Valid @RequestBody EscalateRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
		return service.escalate(requestId, body, user.id());
	}

}
