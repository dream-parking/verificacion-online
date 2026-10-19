package com.dreamparking.backend.liveness.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.liveness.entity.LivenessCheck;
import com.dreamparking.backend.liveness.entity.LivenessStatus;

/**
 * What the app receives after the liveness check. The confidence is a fraud signal, so it is not returned: the app
 * only learns whether the check ran, and the score and the console use the confidence.
 */
public record LivenessCheckResponse(
		@Schema(description = "`CHECKED`: la prueba se hizo. `FAILED`: el proveedor no está disponible; la solicitud sigue y esta señal no cuenta en el score") LivenessStatus status,
		@Schema(description = "Pruebas hechas en esta solicitud", example = "1") short attempts) {

	public static LivenessCheckResponse of(LivenessCheck check) {
		return new LivenessCheckResponse(check.getStatus(), check.getAttempts());
	}

}
