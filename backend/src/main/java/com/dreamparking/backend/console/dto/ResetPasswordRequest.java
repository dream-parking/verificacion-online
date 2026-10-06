package com.dreamparking.backend.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResetPasswordRequest(
		@Schema(description = "Contraseña nueva, de 10 a 72 caracteres") @NotBlank @Size(max = 200) String newPassword) {
}
