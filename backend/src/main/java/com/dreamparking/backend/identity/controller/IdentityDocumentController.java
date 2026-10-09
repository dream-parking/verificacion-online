package com.dreamparking.backend.identity.controller;

import java.io.IOException;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dreamparking.backend.common.exception.InvalidInputException;
import com.dreamparking.backend.identity.dto.IdentityDocumentResponse;
import com.dreamparking.backend.identity.service.IdentityDocumentService;

@RestController
@RequestMapping("/api/onboarding/requests/{requestId}/identity-document")
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class IdentityDocumentController {

	private final IdentityDocumentService service;

	public IdentityDocumentController(IdentityDocumentService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "200", description = "Datos leídos del DUI, para que la persona los confirme o corrija en los datos básicos")
	@Operation(summary = "Paso 2: fotos del DUI (frente y reverso) y lectura de sus datos",
			description = "Multipart con `front` y `back`, JPEG o PNG de hasta 5 MB cada una. Guarda las fotos cifradas en el "
					+ "expediente y lee sus datos. Si el lector no está disponible responde `status: FAILED` y el paso avanza "
					+ "igual: la persona escribe sus datos a mano. Se puede repetir mientras la solicitud está en progreso.")
	@ApiResponse(responseCode = "400", description = "Falta una foto, está vacía o no es JPEG ni PNG", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud ya no está en progreso", content = @Content)
	@ApiResponse(responseCode = "413", description = "Una foto pasa de 5 MB", content = @Content)
	@ApiResponse(responseCode = "422", description = "Las fotos no se pueden leer; `reason` dice por qué (BLURRY, GLARE, CROPPED, "
			+ "TOO_DARK, NOT_A_DUI, WRONG_SIDES, OTHER) y el paso no avanza", content = @Content)
	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public IdentityDocumentResponse capture(@PathVariable UUID requestId, @RequestPart("front") MultipartFile front,
			@RequestPart("back") MultipartFile back) {
		return service.capture(requestId, bytes(front), bytes(back));
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
