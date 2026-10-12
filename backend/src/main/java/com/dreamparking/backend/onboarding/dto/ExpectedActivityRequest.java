package com.dreamparking.backend.onboarding.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of step 5 (expected activity): the type of money and the monthly amount range, both from the catalogs. The rest
 * is optional and makes the declared profile (the baseline of VDI-3) more precise.
 */
public record ExpectedActivityRequest(
		@Schema(description = "Código del tipo de transacción principal (`transactionTypes` de GET /api/catalogs)", example = "PAGO_SALARIO") @NotBlank String transactionTypeCode,
		@Schema(description = "Código del rango de monto mensual (`monthlyAmountRanges` de GET /api/catalogs)", example = "200_500") @NotBlank String monthlyAmountRangeCode,
		@Schema(description = "Otros tipos de dinero que también moverá, además del principal (opcional, hasta 3)", example = "[\"REMESAS\"]") @Size(max = 3) List<@NotBlank String> additionalTransactionTypeCodes,
		@Schema(description = "Monto mensual estimado en USD; debe estar dentro del rango elegido (opcional)", example = "350.00") @Positive @Digits(integer = 12, fraction = 2) BigDecimal monthlyAmountUsd,
		@Schema(description = "Cantidad de depósitos o pagos que recibirá al mes (opcional)", example = "2") @Min(1) @Max(1000) Integer monthlyTransactionCount,
		@Schema(description = "Dinero que saldrá de la cuenta al mes en USD: retiros, transferencias y pagos (opcional)", example = "300.00") @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal monthlyOutflowUsd) {
}
