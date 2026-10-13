package com.dreamparking.backend.account.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dreamparking.backend.account.dto.MovementResponse;
import com.dreamparking.backend.account.dto.ProfileComparisonResponse;
import com.dreamparking.backend.account.dto.RegisterMovementRequest;
import com.dreamparking.backend.account.dto.RegisteredMovementResponse;
import com.dreamparking.backend.account.service.ProfileTrackingService;

/** VDI-3: movements of an account and their comparison with the profile declared in the onboarding request. */
@RestController
@RequestMapping("/api/console/accounts/{accountId}")
@Tag(name = "Consola · Perfil transaccional", description = "Movimientos reales de la cuenta comparados con el perfil declarado")
@SecurityRequirement(name = "bearerAuth")
public class AccountActivityController {

	private static final String MONTH_DESCRIPTION = "Mes a consultar (AAAA-MM, calendario de El Salvador); por defecto el actual";

	private final ProfileTrackingService profileTrackingService;

	public AccountActivityController(ProfileTrackingService profileTrackingService) {
		this.profileTrackingService = profileTrackingService;
	}

	@ApiResponse(responseCode = "201", description = "Movimiento registrado y desviaciones nuevas del mes")
	@Operation(summary = "Registra un movimiento reportado por el core y lo compara con el perfil declarado",
			description = "Suma el mes del movimiento y lo compara con lo declarado. La primera vez que aparece un tipo de "
					+ "desviación en el mes se guarda y genera una alerta (PERFIL_EXCEDIDO u ORIGEN_DISTINTO).")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o tipo de transacción desconocido", content = @Content)
	@ApiResponse(responseCode = "404", description = "La cuenta no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La cuenta está cerrada o el movimiento ya estaba registrado", content = @Content)
	@PostMapping("/movements")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisteredMovementResponse register(@PathVariable UUID accountId,
			@Valid @RequestBody RegisterMovementRequest body) {
		return profileTrackingService.register(accountId, body);
	}

	@ApiResponse(responseCode = "200", description = "Movimientos del mes, del más antiguo al más reciente")
	@Operation(summary = "Movimientos de una cuenta en un mes")
	@ApiResponse(responseCode = "400", description = "Mes inválido", content = @Content)
	@ApiResponse(responseCode = "404", description = "La cuenta no existe", content = @Content)
	@GetMapping("/movements")
	public List<MovementResponse> movements(@PathVariable UUID accountId,
			@Parameter(description = MONTH_DESCRIPTION, schema = @Schema(type = "string", example = "2026-10"))
			@RequestParam(required = false) YearMonth month) {
		return profileTrackingService.movements(accountId, month);
	}

	@ApiResponse(responseCode = "200", description = "Perfil declarado, actividad real y desviaciones del mes")
	@Operation(summary = "Compara la actividad real de un mes con el perfil transaccional declarado")
	@ApiResponse(responseCode = "400", description = "Mes inválido", content = @Content)
	@ApiResponse(responseCode = "404", description = "La cuenta no existe", content = @Content)
	@ApiResponse(responseCode = "409", description = "La cuenta no se abrió desde una solicitud con perfil declarado", content = @Content)
	@GetMapping("/profile-comparison")
	public ProfileComparisonResponse compare(@PathVariable UUID accountId,
			@Parameter(description = MONTH_DESCRIPTION, schema = @Schema(type = "string", example = "2026-10"))
			@RequestParam(required = false) YearMonth month) {
		return profileTrackingService.compare(accountId, month);
	}

}
