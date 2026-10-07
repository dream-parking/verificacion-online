package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Body of step 2 (income). {@code sourceDetail} is required when the source is {@code OTRO}. */
public record IncomeDeclarationRequest(
		@Schema(description = "Código de la fuente de ingreso (catálogo)", example = "SALARIO") @NotBlank String sourceCode,
		@Schema(description = "Detalle libre; obligatorio si la fuente es OTRO", example = "Venta de artesanías") @Size(max = 150) String sourceDetail,
		@Schema(description = "Código del rango de ingreso (catálogo)", example = "500_1500") @NotBlank String rangeCode) {
}
