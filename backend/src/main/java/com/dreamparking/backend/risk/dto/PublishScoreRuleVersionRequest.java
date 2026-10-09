package com.dreamparking.backend.risk.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.FreeText;
import com.dreamparking.backend.risk.entity.enums.RuleStatus;

/**
 * New version of a score rule: same logic as the latest version, with a new threshold and status. DRAFT versions
 * are stored but not applied; PROVISIONAL and CONFIRMED replace the version in force.
 */
public record PublishScoreRuleVersionRequest(
		@Schema(description = "Umbral mensual en USD", example = "600") @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal threshold,
		@Schema(description = "DRAFT, PROVISIONAL o CONFIRMED", example = "CONFIRMED") @NotNull RuleStatus status,
		@Schema(description = "Motivo del cambio", example = "Umbral confirmado por Conozca a su Cliente") @Size(max = 250) @FreeText String statusNote) {
}
