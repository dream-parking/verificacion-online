package com.dreamparking.backend.outcome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.FreeText;
import com.dreamparking.backend.outcome.entity.enums.CaseOutcome;

/** The outcome of a case. The reason also goes to the risk list log, so it is shorter than the lists' own. */
public record RegisterOutcomeRequest(
		@Schema(description = "FRAUDE_CONFIRMADO (el DUI, el teléfono y el dispositivo pasan a la lista de fraude confirmado) o FALSO_POSITIVO (solo se registra)", example = "FRAUDE_CONFIRMADO") @NotNull CaseOutcome outcome,
		@Schema(description = "Por qué se cierra así", example = "El titular del DUI negó haber abierto la cuenta") @NotBlank @Size(max = 150) @FreeText String reason) {
}
