package com.dreamparking.backend.console.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.common.dto.PageResponse;
import com.dreamparking.backend.common.validation.FreeText;
import com.dreamparking.backend.console.dto.ConsoleRequestDetail;
import com.dreamparking.backend.console.dto.ConsoleRequestListItem;
import com.dreamparking.backend.console.dto.ConsoleRequestLocation;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleRequestService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.onboarding.entity.enums.RequestStatus;
import com.dreamparking.backend.risk.entity.enums.RiskLevel;
import com.dreamparking.backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/console/requests")
@Tag(name = "Consola · Solicitudes", description = "Listado y detalle de solicitudes para la consola administrativa")
@SecurityRequirement(name = "bearerAuth")
public class ConsoleRequestController {

	static final String AUDIT_ACTION = "VIEW_REQUEST_DETAIL";

	private final ConsoleRequestService service;

	private final ConsoleUserService users;

	private final AccessAuditService audit;

	public ConsoleRequestController(ConsoleRequestService service, ConsoleUserService users,
			AccessAuditService audit) {
		this.service = service;
		this.users = users;
		this.audit = audit;
	}

	@Operation(summary = "Listado de solicitudes, de la más reciente a la más antigua",
			description = "Filtra por estado, nivel de riesgo y texto (nombre o número). `page` empieza en 0; `size` máximo 100.")
	@GetMapping
	public PageResponse<ConsoleRequestListItem> list(@RequestParam(required = false) RequestStatus status,
			@RequestParam(required = false) RiskLevel riskLevel,
			@RequestParam(required = false) @Size(max = 100) @FreeText String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(status, riskLevel, q, page, size);
	}

	@Operation(summary = "Ubicación de cada solicitud, para el mapa",
			description = "Todas las solicitudes, de la más reciente a la más antigua, con la ubicación que el servidor obtuvo "
					+ "de la IP. No trae datos del DUI, así que no queda en la auditoría de accesos.")
	@GetMapping("/locations")
	public List<ConsoleRequestLocation> locations() {
		return service.locations();
	}

	@ApiResponse(responseCode = "200", description = "Detalle de la solicitud")
	@Operation(summary = "Detalle de una solicitud",
			description = "Datos declarados, DUI (lo leído, lo confirmado y los campos corregidos), ingresos, movimiento esperado, "
					+ "score, señales, tiempo por paso y línea de tiempo. Trae datos personales: cada consulta queda en la "
					+ "auditoría de accesos del usuario y la respuesta no se guarda en caché.")
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@GetMapping("/{requestId}")
	public ResponseEntity<ConsoleRequestDetail> detail(@PathVariable UUID requestId,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
		ConsoleRequestDetail detail = service.detail(requestId);
		audit.record(users.activeUser(user.id()), AUDIT_ACTION, "onboarding_request", requestId.toString(),
				clientIp(http));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detail);
	}

	private static InetAddress clientIp(HttpServletRequest http) {
		try {
			return InetAddress.getByName(http.getRemoteAddr());
		}
		catch (UnknownHostException ex) {
			return null;
		}
	}

}
