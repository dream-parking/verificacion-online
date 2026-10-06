package com.dreamparking.backend.onboarding;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body of step 3 (expected activity): how the customer will use the account and the monthly amount. */
public record ExpectedActivityRequest(
		@Schema(description = "Código del tipo de transacción (catálogo)", example = "PAGO_SALARIO") @NotBlank String transactionTypeCode,
		@Schema(description = "Monto mensual esperado en USD", example = "350.00") @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal monthlyAmountUsd) {
}
