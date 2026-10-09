package com.dreamparking.backend.identity.controller;

import java.io.IOException;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.identity.dto.ConfirmIdentityDocumentRequest;
import com.dreamparking.backend.identity.dto.IdentityDocumentResponse;
import com.dreamparking.backend.identity.service.IdentityDocumentService;
import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;

@RestController
@RequestMapping("/api/onboarding/requests/{requestId}/identity-document")
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class IdentityDocumentController {

	private final IdentityDocumentService service;

	public IdentityDocumentController(IdentityDocumentService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "200", description = "Datos leídos del DUI, para que la persona los confirme o corrija en los datos básicos")
	@Operation(summary = "Paso 3: fotos del DUI (frente y reverso) y lectura de sus datos",
			description = "Va después de los datos básicos. Multipart con `front` y `back`, JPEG o PNG de hasta 5 MB cada una. "
					+ "Guarda las fotos cifradas en el expediente y lee sus datos para la pantalla de confirmación; un dato que "
					+ "no se leyó con claridad viene `null`. Si el lector no está disponible responde `status: FAILED` y la "
					+ "persona escribe sus datos al confirmar. El paso se completa al confirmar. Se puede repetir mientras la "
					+ "solicitud está en progreso; las fotos nuevas piden confirmar otra vez.")
	@ApiResponse(responseCode = "400", description = "Falta una foto, está vacía o no es JPEG ni PNG", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "Faltan los datos básicos o la solicitud ya no está en progreso", content = @Content)
	@ApiResponse(responseCode = "413", description = "Una foto pasa de 5 MB", content = @Content)
	@ApiResponse(responseCode = "422", description = "Las fotos no se pueden leer: `reason` dice por qué (BLURRY, GLARE, CROPPED, "
			+ "TOO_DARK, NOT_A_DUI, WRONG_SIDES, OTHER) y `side` qué foto repetir (FRONT, BACK, BOTH; puede faltar)", content = @Content)
	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public IdentityDocumentResponse capture(@PathVariable UUID requestId, @RequestPart("front") MultipartFile front,
			@RequestPart("back") MultipartFile back) {
		return service.capture(requestId, bytes(front), bytes(back));
	}

	@ApiResponse(responseCode = "200", description = "Resumen de la solicitud con el paso completado")
	@Operation(summary = "Paso 3: confirma o corrige los datos leídos del DUI",
			description = "Lo que la persona confirma en la pantalla «¿Leímos bien tus datos?». Se guarda junto a lo leído (la "
					+ "consola ve qué se corrigió) y se compara con los datos básicos. Completa el paso del DUI.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "Faltan los datos básicos o las fotos legibles del DUI, o la solicitud ya no está en progreso", content = @Content)
	@PutMapping("/confirmation")
	public OnboardingRequestResponse confirm(@PathVariable UUID requestId,
			@Valid @RequestBody ConfirmIdentityDocumentRequest body) {
		return service.confirm(requestId, body);
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new InvalidInputException("The photo " + file.getName() + " could not be received");
		}
	}

}
