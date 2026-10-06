package com.dreamparking.backend.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
		@Schema(description = "Correo institucional", example = "abeltran@ceiba.example") @NotBlank @Size(max = 150) String email,
		@Schema(description = "Contraseña", example = "una-clave-larga-1") @NotBlank @Size(max = 200) String password) {
}
