package com.dreamparking.backend.onboarding;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.risk.RiskAssessmentResponse;

@RestController
@RequestMapping("/api/onboarding/requests")
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class OnboardingController {

	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	@Operation(summary = "Inicia una solicitud de onboarding")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OnboardingRequestResponse start() {
		return onboardingService.start();
	}

	@Operation(summary = "Consulta el resumen de una solicitud")
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@GetMapping("/{requestId}")
	public OnboardingRequestResponse get(@PathVariable UUID requestId) {
		return onboardingService.get(requestId);
	}

	@Operation(summary = "Paso 2: declara fuente y rango de ingresos",
			description = "Guarda o reemplaza la declaración. `sourceDetail` es obligatorio cuando la fuente es `OTRO`.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o código de catálogo desconocido", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud ya no está en progreso", content = @Content)
	@PutMapping("/{requestId}/income")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void declareIncome(@PathVariable UUID requestId, @Valid @RequestBody IncomeDeclarationRequest body) {
		onboardingService.declareIncome(requestId, body);
	}

	/** Returns the risk score assigned from the declared monthly amount. */
	@Operation(summary = "Paso 3: registra el movimiento esperado y asigna el score de riesgo",
			description = "Guarda o reemplaza la actividad esperada y devuelve el score calculado con el monto mensual.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o código de catálogo desconocido", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud ya no está en progreso", content = @Content)
	@PutMapping("/{requestId}/expected-activity")
	public RiskAssessmentResponse registerExpectedActivity(@PathVariable UUID requestId,
			@Valid @RequestBody ExpectedActivityRequest body) {
		return onboardingService.registerExpectedActivity(requestId, body);
	}

}
