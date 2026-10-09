package com.dreamparking.backend.onboarding.controller;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.onboarding.dto.BasicDataRequest;
import com.dreamparking.backend.onboarding.dto.CaptureSignalsRequest;
import com.dreamparking.backend.onboarding.dto.ExpectedActivityRequest;
import com.dreamparking.backend.onboarding.dto.IncomeDeclarationRequest;
import com.dreamparking.backend.onboarding.dto.OnboardingRequestResponse;
import com.dreamparking.backend.onboarding.dto.PrivacyConsentRequest;
import com.dreamparking.backend.onboarding.service.OnboardingService;
import com.dreamparking.backend.onboarding.service.SignalsService;
import com.dreamparking.backend.risk.dto.RiskAssessmentResponse;

@RestController
@RequestMapping("/api/onboarding/requests")
@Tag(name = "Onboarding", description = "Pasos de la solicitud de apertura de cuenta (app móvil)")
public class OnboardingController {

	private final OnboardingService onboardingService;

	private final SignalsService signalsService;

	public OnboardingController(OnboardingService onboardingService, SignalsService signalsService) {
		this.onboardingService = onboardingService;
		this.signalsService = signalsService;
	}

	@Operation(summary = "Inicia una solicitud de onboarding")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OnboardingRequestResponse start() {
		return onboardingService.start();
	}

	@ApiResponse(responseCode = "200", description = "Resumen de la solicitud")
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
	@ApiResponse(responseCode = "200", description = "Score de riesgo asignado")
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

	@Operation(summary = "Captura las señales de dispositivo y comportamiento",
			description = "Guarda o reemplaza las señales de la sesión: huella y modelo del dispositivo, ubicación aproximada, "
					+ "ritmo de escritura y tiempo por paso. La IP, el user agent y la hora los toma el servidor de la conexión.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud ya no está en progreso", content = @Content)
	@PutMapping("/{requestId}/signals")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void captureSignals(@PathVariable UUID requestId, @Valid @RequestBody CaptureSignalsRequest body,
			HttpServletRequest http, @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		signalsService.capture(requestId, body, http.getRemoteAddr(), userAgent);
	}

	@ApiResponse(responseCode = "200", description = "Resumen de la solicitud con el paso completado")
	@Operation(summary = "Acepta el aviso de privacidad vigente",
			description = "Registra la aceptación, incluida la captura de señales, y la IP desde la que se aceptó.")
	@ApiResponse(responseCode = "400", description = "`signalsAccepted` debe ser `true`", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud ya no está en progreso o no hay aviso vigente", content = @Content)
	@PutMapping("/{requestId}/privacy-consent")
	public OnboardingRequestResponse acceptPrivacyNotice(@PathVariable UUID requestId,
			@Valid @RequestBody PrivacyConsentRequest body, HttpServletRequest http) {
		return onboardingService.acceptPrivacyNotice(requestId, clientIp(http));
	}

	@ApiResponse(responseCode = "200", description = "Resumen de la solicitud con el paso completado")
	@Operation(summary = "Registra los datos básicos del cliente",
			description = "DUI (00000000-0), nombres, apellidos y celular (7000-0000). Un DUI ya registrado con otros nombres o celular, o un celular de otro DUI, se rechaza con 409.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content)
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La solicitud ya no está en progreso, el DUI ya está registrado o el celular ya está en uso", content = @Content)
	@PutMapping("/{requestId}/basic-data")
	public OnboardingRequestResponse registerBasicData(@PathVariable UUID requestId,
			@Valid @RequestBody BasicDataRequest body) {
		return onboardingService.registerBasicData(requestId, body);
	}

	@ApiResponse(responseCode = "200", description = "Solicitud enviada, con su número SOL-AAAA-NNNNN")
	@Operation(summary = "Envía la solicitud",
			description = "Requiere los pasos anteriores completos. Asigna el número y deja la solicitud como completada.")
	@ApiResponse(responseCode = "404", description = "La solicitud no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "Faltan pasos o la solicitud ya no está en progreso", content = @Content)
	@PostMapping("/{requestId}/submit")
	public OnboardingRequestResponse submit(@PathVariable UUID requestId) {
		return onboardingService.submit(requestId);
	}

	/** Client address as seen by the servlet container (a literal IP, so no DNS lookup happens). */
	private static InetAddress clientIp(HttpServletRequest http) {
		try {
			return InetAddress.getByName(http.getRemoteAddr());
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException("Invalid client address: " + http.getRemoteAddr(), ex);
		}
	}

}
