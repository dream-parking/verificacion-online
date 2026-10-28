package com.dreamparking.backend.risklist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.FreeText;
import com.dreamparking.backend.common.validation.SafeText;
import com.dreamparking.backend.risklist.entity.enums.RiskListDataType;

/**
 * Data to put on a risk list. The value is hashed and never stored or returned; it must have the shape of its type
 * (a DUI with or without hyphen, a mobile phone with or without hyphen or +503, a device fingerprint as the app sends it).
 */
public record CreateRiskListEntryRequest(
		@Schema(description = "DOCUMENT (DUI), PHONE o DEVICE", example = "DOCUMENT") @NotNull RiskListDataType dataType,
		@Schema(description = "El dato: DUI (04567891-2), teléfono celular (7845-2310) o huella del dispositivo. Se guarda solo su huella", example = "04567891-2") @NotBlank @Size(max = 64) @SafeText String value,
		@Schema(description = "Motivo de la alta", example = "Fraude confirmado en el caso 2026-114") @NotBlank @Size(max = 250) @FreeText String reason) {
}
