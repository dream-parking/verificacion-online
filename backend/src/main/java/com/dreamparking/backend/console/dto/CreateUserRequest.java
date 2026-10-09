package com.dreamparking.backend.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

import com.dreamparking.backend.common.validation.EmailAddress;
import com.dreamparking.backend.common.validation.FreeText;
import com.dreamparking.backend.common.validation.Password;
import com.dreamparking.backend.common.validation.PersonName;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;

/** Body to create a console user. Initials are derived from the name. */
public record CreateUserRequest(
		@Schema(description = "Correo institucional; es el usuario para iniciar sesión", example = "nuevo@ceiba.example") @NotBlank @EmailAddress @Size(max = 150) String email,
		@Schema(example = "Nuevo Analista") @NotBlank @Size(max = 150) @PersonName String fullName,
		@Schema(example = "Analista de fraude y cumplimiento") @NotBlank @Size(max = 120) @FreeText String jobTitle,
		@NotNull ConsoleRole role,
		@Schema(description = "Contraseña inicial, de 10 a 72 caracteres; la persona debe cambiarla al entrar") @NotBlank @Size(max = 200) @Password String initialPassword) {
}
