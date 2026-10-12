package com.dreamparking.backend.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.account.entity.enums.MovementDirection;

/** A movement of the account reported by the core banking system (VDI-3). */
public record RegisterMovementRequest(
		@Schema(description = "Id del movimiento en el core; no se acepta dos veces para la misma cuenta", example = "core-20261012-000123") @NotBlank @Size(max = 64) String externalId,
		@Schema(description = "IN si el dinero entra a la cuenta, OUT si sale") @NotNull MovementDirection direction,
		@Schema(description = "Monto en USD", example = "350.00") @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amountUsd,
		@Schema(description = "Tipo de dinero según el core (`transactionTypes` de GET /api/catalogs); vacío si no está clasificado", example = "PAGO_SALARIO") String transactionTypeCode,
		@Schema(description = "Descripción del core", example = "Planilla octubre") @Size(max = 150) String description,
		@Schema(description = "Fecha y hora del movimiento") @NotNull @PastOrPresent Instant occurredAt) {
}
