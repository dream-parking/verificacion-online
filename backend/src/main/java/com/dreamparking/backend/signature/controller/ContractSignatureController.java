package com.dreamparking.backend.signature.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.signature.dto.ContractResponse;
import com.dreamparking.backend.signature.dto.ContractSignatureResponse;
import com.dreamparking.backend.signature.dto.SignContractRequest;
import com.dreamparking.backend.signature.service.ContractSignatureService;
import com.dreamparking.backend.signature.service.ContractTerms;

/** The account contract and its electronic signature, for the app (VDI-130). */
@RestController
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class ContractSignatureController {

	private final ContractSignatureService service;

	private final ContractTerms terms;

	public ContractSignatureController(ContractSignatureService service, ContractTerms terms) {
		this.service = service;
		this.terms = terms;
	}

	@ApiResponse(responseCode = "200", description = "El contrato vigente")
	@Operation(summary = "Contrato de apertura vigente",
			description = "Versión, texto y huella SHA-256 del contrato que la app muestra antes de firmar. Al firmar, la "
					+ "app manda esa misma `version`.")
	@GetMapping("/api/onboarding/contract")
	public ContractResponse contract() {
		return ContractResponse.of(terms);
	}

	@ApiResponse(responseCode = "201", description = "Contrato firmado; la evidencia quedó en el expediente")
	@Operation(summary = "Firma electrónica del contrato",
			description = "Firma el contrato vigente con el servicio de firma (simulado mientras no hay un proveedor "
					+ "real). Solo una solicitud enviada y aprobada (`COMPLETED`) puede firmar, una sola vez. Guarda la "
					+ "versión, la huella SHA-256 del texto firmado, la fecha y hora, y el dispositivo y la IP de la sesión; "
					+ "esa evidencia no se puede cambiar ni borrar.")
	@ApiResponse(responseCode = "400", description = "Falta `contractVersion` o es demasiado larga", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud no está aprobada (en progreso, en revisión reforzada, "
			+ "en espera del RNPN o abandonada), ya firmó, o la versión no es la vigente", content = @Content)
	@ApiResponse(responseCode = "422", description = "El servicio de firma rechazó la firma: no se guarda y se puede "
			+ "intentar de nuevo", content = @Content)
	@PostMapping("/api/onboarding/requests/{requestId}/signature")
	@ResponseStatus(HttpStatus.CREATED)
	public ContractSignatureResponse sign(@PathVariable UUID requestId, @Valid @RequestBody SignContractRequest body,
			HttpServletRequest http, @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT,
					required = false) String userAgent) {
		return service.sign(requestId, body.contractVersion(), http.getRemoteAddr(), userAgent);
	}

}
