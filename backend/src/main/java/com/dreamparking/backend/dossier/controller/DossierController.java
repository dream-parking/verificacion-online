package com.dreamparking.backend.dossier.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.dossier.dto.ArchivedDossierResponse;
import com.dreamparking.backend.dossier.service.DossierLookupService;
import com.dreamparking.backend.security.AuthenticatedUser;

/**
 * Archived binding dossiers by request or customer number (VDI-135), for any console user that can see requests. Each
 * query is recorded in the access audit, since a dossier holds personal data.
 */
@RestController
@RequestMapping("/api/console/dossiers")
@Tag(name = "Consola · Expedientes", description = "Expediente de vinculación archivado, por número de solicitud o de cliente")
@SecurityRequirement(name = "bearerAuth")
public class DossierController {

	static final String AUDIT_ACTION = "VIEW_BINDING_DOSSIER";

	private final DossierLookupService lookup;

	private final ConsoleUserService users;

	private final AccessAuditService audit;

	public DossierController(DossierLookupService lookup, ConsoleUserService users, AccessAuditService audit) {
		this.lookup = lookup;
		this.users = users;
		this.audit = audit;
	}

	@ApiResponse(responseCode = "200", description = "Por `requestNumber`, el expediente; por `customerNumber`, la lista de "
			+ "expedientes del cliente (uno por solicitud firmada), del más reciente al más antiguo, vacía si no ha firmado",
			content = @Content(mediaType = "application/json",
					schema = @Schema(oneOf = { ArchivedDossierResponse.class, ArchivedDossierResponse[].class })))
	@Operation(summary = "Busca el expediente de vinculación por número de solicitud o de cliente",
			description = "Envía uno de los dos filtros. El expediente es la copia inmutable que se congeló al firmar. Trae "
					+ "datos personales: cada consulta queda en la auditoría de accesos (`VIEW_BINDING_DOSSIER`, con el "
					+ "número consultado, el usuario y la IP) y la respuesta no se guarda en caché.")
	@ApiResponse(responseCode = "400", description = "Ninguno o los dos filtros, o un número con formato inválido",
			content = @Content)
	@ApiResponse(responseCode = "404", description = "No hay solicitud o cliente con ese número, o la solicitud aún no firmó",
			content = @Content)
	@GetMapping
	public ResponseEntity<Object> find(
			@Parameter(description = "Número de solicitud", example = "SOL-2026-00001") @RequestParam(required = false)
			@Pattern(regexp = "SOL-[0-9]{4}-[0-9]{5,}") @Size(max = 20) String requestNumber,
			@Parameter(description = "Número de cliente", example = "CLI-0000001") @RequestParam(required = false)
			@Pattern(regexp = "CLI-[0-9]{7,}") @Size(max = 20) String customerNumber,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
		if ((requestNumber == null) == (customerNumber == null)) {
			throw new InvalidInputException("Send either requestNumber or customerNumber, not both");
		}
		Object found = requestNumber != null ? lookup.byRequestNumber(requestNumber)
				: lookup.byCustomerNumber(customerNumber);
		audit.record(users.activeUser(user.id()), AUDIT_ACTION, "binding_dossier",
				requestNumber != null ? requestNumber : customerNumber, clientIp(http));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(found);
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
