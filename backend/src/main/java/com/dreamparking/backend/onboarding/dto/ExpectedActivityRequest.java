package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.Code;

/** Body of step 3 (expected activity): the type of money and the monthly amount range, both from the catalogs. */
public record ExpectedActivityRequest(
		@Schema(description = "Código del tipo de transacción (`transactionTypes` de GET /api/catalogs)", example = "PAGO_SALARIO") @NotBlank @Size(max = 30) @Code String transactionTypeCode,
		@Schema(description = "Código del rango de monto mensual (`monthlyAmountRanges` de GET /api/catalogs)", example = "200_500") @NotBlank @Size(max = 30) @Code String monthlyAmountRangeCode) {
}
