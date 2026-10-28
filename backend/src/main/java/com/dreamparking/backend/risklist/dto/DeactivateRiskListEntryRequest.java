package com.dreamparking.backend.risklist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.FreeText;

/** Why an entry leaves the list. */
public record DeactivateRiskListEntryRequest(
		@Schema(description = "Motivo de la baja", example = "Se descartó el fraude tras la investigación") @NotBlank @Size(max = 250) @FreeText String reason) {
}
