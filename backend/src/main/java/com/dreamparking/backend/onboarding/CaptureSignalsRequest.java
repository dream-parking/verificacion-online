package com.dreamparking.backend.onboarding;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Device and behavior signals sent by the mobile app. The IP address and the capture time are not part of the body:
 * the server takes them from the connection, so the client cannot forge them.
 */
public record CaptureSignalsRequest(
		@Schema(description = "Huella del dispositivo (hash normalizado)", example = "d4f1·9a3c·e7b2") @NotBlank @Size(max = 64) String deviceFingerprint,
		@Schema(description = "Modelo del dispositivo", example = "iPhone 15") @Size(max = 80) String deviceModel,
		@Schema(description = "Sistema operativo", example = "iOS") @Size(max = 30) String operatingSystem,
		@Schema(description = "Versión de la app", example = "1.0.0+1") @Size(max = 20) String appVersion,
		@Schema(description = "Ubicación aproximada reportada por la app", example = "San Salvador, El Salvador") @Size(max = 120) String approximateLocation,
		@Schema(description = "País, ISO 3166-1 alfa-2", example = "SV") @Pattern(regexp = "^[A-Za-z]{2}$") String countryIso,
		@Schema(description = "Ritmo de escritura en caracteres por minuto; el servidor lo clasifica", example = "185") @Min(0) @Max(2000) Short typingSpeedCpm,
		@Schema(description = "Tiempo por paso del formulario") @Valid @Size(max = 5) List<StepTiming> steps) {

	/** Time spent on one step of the form. */
	public record StepTiming(@NotNull OnboardingStep step, @NotNull Instant startedAt, Instant completedAt,
			@Min(1) Short attempts) {
	}

}
