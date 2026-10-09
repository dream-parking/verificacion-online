package com.dreamparking.backend.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.alert.entity.enums.AlertCriticality;

/** New type of alert. The code cannot change afterwards: alerts reference it. */
public record CreateAlertTypeRequest(
		@Schema(description = "Código único, en mayúsculas", example = "RETIROS_FRACCIONADOS") @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,40}") String code,
		@Schema(description = "Descripción que ve el analista", example = "Retiros fraccionados para no superar el umbral") @NotBlank @Size(max = 200) String description,
		@Schema(description = "Criticidad con la que se crean las alertas de este tipo", example = "HIGH") @NotNull AlertCriticality defaultCriticality) {
}
