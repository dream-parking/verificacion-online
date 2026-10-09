package com.dreamparking.backend.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.Code;
import com.dreamparking.backend.common.validation.MultilineText;

/** Closes an alert with a short resolution code and an optional comment. */
public record CloseAlertRequest(
		@Schema(description = "Código de resolución", example = "FALSO_POSITIVO") @NotBlank @Size(max = 30) @Code String resolution,
		@Schema(description = "Comentario para el historial de la alerta", example = "Pago de aguinaldo") @Size(max = 2000) @MultilineText String comment) {
}
