package com.dreamparking.backend.risk.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * New score thresholds (VDI-120): where the LOW and MEDIUM levels end. The weights are copied from the version in
 * force. Both are whole numbers; the service checks it so the answer says why in Spanish.
 */
public record PublishScoreModelVersionRequest(
		@Schema(description = "Último puntaje del nivel Bajo (entero, desde 0)", example = "39") @NotNull BigDecimal lowMax,
		@Schema(description = "Último puntaje del nivel Medio (entero, menor que 100). Arriba de este valor el nivel es Alto "
				+ "y la solicitud pasa a revisión reforzada", example = "69") @NotNull BigDecimal mediumMax) {
}
