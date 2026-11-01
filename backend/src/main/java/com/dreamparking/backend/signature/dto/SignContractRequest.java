package com.dreamparking.backend.signature.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the app sends to sign (VDI-130): the version of the contract it showed the customer. The backend owns the text
 * and computes its fingerprint, so the app never sends the text itself.
 */
public record SignContractRequest(@Schema(description = "Versión del contrato que la app mostró a la persona, tal como "
		+ "la devolvió GET /api/onboarding/contract", example = "2026.1") @NotBlank @Size(max = 30) @Pattern(regexp = "[0-9A-Za-z][0-9A-Za-z._-]*", message = "Debe ser una versión: letras, números, ., _ y -") String contractVersion) {
}
