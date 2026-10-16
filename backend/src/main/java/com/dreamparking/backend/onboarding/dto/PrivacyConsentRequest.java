package com.dreamparking.backend.onboarding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Step 1: the customer must accept the privacy notice, including the capture of session signals. The acceptance of
 * the terms and conditions (VDI-93) is recorded when the app sends it; it cannot be {@code false}.
 */
public record PrivacyConsentRequest(
		@Schema(description = "Acepta el aviso de privacidad y la captura de señales; debe ser `true`", example = "true") @NotNull @AssertTrue Boolean signalsAccepted,
		@Schema(description = "Acepta los términos y condiciones; si se envía debe ser `true`. Sin él queda «sin registro»", example = "true") @AssertTrue Boolean termsAccepted) {
}
