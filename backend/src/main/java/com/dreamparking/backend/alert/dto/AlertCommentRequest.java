package com.dreamparking.backend.alert.dto;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.MultilineText;

/** Optional comment that goes to the alert history. */
public record AlertCommentRequest(
		@Schema(description = "Comentario para el historial de la alerta", example = "Revisando estados de cuenta") @Size(max = 2000) @MultilineText String comment) {
}
