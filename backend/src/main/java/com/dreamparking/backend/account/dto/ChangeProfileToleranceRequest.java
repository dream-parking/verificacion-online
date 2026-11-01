package com.dreamparking.backend.account.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * New tolerance of the declared profile tracking (VDI-137): a whole percentage from 0 to 45. It is a decimal so that
 * 12.5 is refused instead of being read as 12.
 */
public record ChangeProfileToleranceRequest(
		@Schema(description = "Margen sobre lo declarado antes de considerarlo una desviación, en porcentaje entero de 0 a 45",
				example = "25") @NotNull(message = "Indica la tolerancia.")
		@Digits(integer = 3, fraction = 0, message = "La tolerancia debe ser un número entero.")
		@DecimalMin(value = "0", message = "La tolerancia no puede ser menor que 0 %.")
		@DecimalMax(value = "45", message = "La tolerancia no puede ser mayor que 45 %.") BigDecimal tolerancePercent) {
}
