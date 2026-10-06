package com.dreamparking.backend.alert;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of "take alert". Until the console has sign-in, the caller says which analyst is taking the alert. */
public record TakeAlertRequest(
		@Schema(description = "Id del usuario de la consola que toma la alerta (de GET /api/console/users)") @NotNull UUID userId) {
}
