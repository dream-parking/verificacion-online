package com.dreamparking.backend.liveness.controller;

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
import com.dreamparking.backend.liveness.dto.LivenessCheckResponse;
import com.dreamparking.backend.liveness.service.LivenessService;

@RestController
@RequestMapping("/api/onboarding/requests/{requestId}/liveness")
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class LivenessController {

	private final LivenessService service;

	public LivenessController(LivenessService service) {
		this.service = service;
	}

	@ApiResponse(responseCode = "200", description = "La prueba quedó registrada en el expediente")
	@Operation(summary = "Prueba de vida: selfie de la persona",
			description = "Multipart con `selfie`, JPEG o PNG de hasta 5 MB. Guarda la selfie cifrada en el expediente y "
					+ "registra el nivel de confianza (de 0 a 1) que da el proveedor; ese número no se devuelve a la app, "
					+ "lo usan el score y la consola. Si el proveedor no está disponible responde `status: FAILED` y la "
					+ "solicitud sigue: esa señal no suma ni resta. Se puede repetir mientras la solicitud está en "
					+ "progreso; cada intento cuenta en `attempts`. Si la foto no sirve por cómo se tomó (poca luz, borrosa, sin "
					+ "rostro visible…) responde 422 con el motivo y no se guarda nada; nunca da motivos de sospecha.")
	@ApiResponse(responseCode = "400", description = "Falta la selfie, está vacía o no es JPEG ni PNG", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud ya no está en progreso", content = @Content)
	@ApiResponse(responseCode = "413", description = "La selfie pasa de 5 MB", content = @Content)
	@ApiResponse(responseCode = "422", description = "La selfie no se puede juzgar por cómo se tomó: `reason` dice por qué "
			+ "(BLURRY, TOO_DARK, GLARE, FACE_NOT_VISIBLE, MULTIPLE_FACES, OTHER) y la app pide repetirla. No se guarda ni "
			+ "cuenta como intento", content = @Content)
	@PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LivenessCheckResponse check(@PathVariable UUID requestId, @RequestPart("selfie") MultipartFile selfie) {
		return service.check(requestId, bytes(selfie));
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
