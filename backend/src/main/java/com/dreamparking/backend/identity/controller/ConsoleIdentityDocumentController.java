package com.dreamparking.backend.identity.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.console.service.AccessAuditService;
import com.dreamparking.backend.console.service.ConsoleUserService;
import com.dreamparking.backend.identity.entity.enums.DocumentSide;
import com.dreamparking.backend.identity.service.IdentityDocumentService;
import com.dreamparking.backend.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/console/requests/{requestId}/identity-document")
@Tag(name = "Consola · Solicitudes", description = "Listado y detalle de solicitudes para la consola administrativa")
@SecurityRequirement(name = "bearerAuth")
public class ConsoleIdentityDocumentController {

	static final String AUDIT_ACTION = "VIEW_IDENTITY_DOCUMENT";

	private final IdentityDocumentService service;

	private final ConsoleUserService users;

	private final AccessAuditService audit;

	public ConsoleIdentityDocumentController(IdentityDocumentService service, ConsoleUserService users,
			AccessAuditService audit) {
		this.service = service;
		this.users = users;
		this.audit = audit;
	}

	@ApiResponse(responseCode = "200", description = "La foto (JPEG o PNG)",
			content = { @Content(mediaType = MediaType.IMAGE_JPEG_VALUE), @Content(mediaType = MediaType.IMAGE_PNG_VALUE) })
	@Operation(summary = "Foto del DUI de una solicitud (frente o reverso)",
			description = "Descifra la foto guardada en el expediente. Cada consulta queda en la auditoría de accesos del usuario "
					+ "y la respuesta no se guarda en caché.")
	@ApiResponse(responseCode = "404", description = "La solicitud no tiene esa foto", content = @Content)
	@GetMapping("/{side}")
	public ResponseEntity<byte[]> photo(@PathVariable UUID requestId, @PathVariable DocumentSide side,
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
		IdentityDocumentService.Photo photo = service.photo(requestId, side);
		audit.record(users.activeUser(user.id()), AUDIT_ACTION, "onboarding_request", requestId + "/" + side,
				clientIp(http));
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(photo.contentType()))
			.cacheControl(CacheControl.noStore())
			.body(photo.content());
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
