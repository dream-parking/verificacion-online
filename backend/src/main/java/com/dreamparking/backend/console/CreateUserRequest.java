package com.dreamparking.backend.console;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body to create a console user. Initials are derived from the name. */
public record CreateUserRequest(
		@Schema(description = "Correo institucional; es el usuario para iniciar sesión", example = "nuevo@ceiba.example") @NotBlank @Email @Size(max = 150) String email,
		@Schema(example = "Nuevo Analista") @NotBlank @Size(max = 150) String fullName,
		@Schema(example = "Analista de fraude y cumplimiento") @NotBlank @Size(max = 120) String jobTitle,
		@NotNull ConsoleRole role,
		@Schema(description = "Contraseña inicial, de 10 a 72 caracteres; la persona debe cambiarla al entrar") @NotBlank @Size(max = 200) String initialPassword) {
}
