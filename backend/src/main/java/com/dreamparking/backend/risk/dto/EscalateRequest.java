package com.dreamparking.backend.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.FreeText;
import com.dreamparking.backend.onboarding.entity.enums.EscalationChannel;

/** Where the analyst sends a request held for enhanced review, and why (VDI-143). */
public record EscalateRequest(
		@Schema(description = "VIDEO_CALL (videollamada) o BRANCH (agencia)", example = "VIDEO_CALL") @NotNull EscalationChannel channel,
		@Schema(description = "Por qué se escala", example = "El monto declarado no coincide con los ingresos") @NotBlank @Size(max = 250) @FreeText String reason) {
}
