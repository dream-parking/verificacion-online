package com.dreamparking.backend.console.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.console.entity.enums.ConsoleRole;

public record CreateConsoleUserRequest(@NotBlank @Email @Size(max = 150) String email,
		@NotBlank @Size(max = 150) String fullName, @NotBlank @Size(max = 120) String jobTitle,
		@NotBlank @Size(max = 3) String initials, @NotNull ConsoleRole role) {
}
