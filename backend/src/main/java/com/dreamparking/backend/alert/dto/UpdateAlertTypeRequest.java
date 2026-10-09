package com.dreamparking.backend.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.alert.entity.enums.AlertCriticality;

/** Edits a type of alert. Alerts already raised keep their own criticality. */
public record UpdateAlertTypeRequest(
		@Schema(description = "Descripción que ve el analista", example = "Movimientos por encima del perfil declarado") @NotBlank @Size(max = 200) String description,
		@Schema(description = "Criticidad con la que se crean las alertas nuevas de este tipo", example = "CRITICAL") @NotNull AlertCriticality defaultCriticality) {
}
