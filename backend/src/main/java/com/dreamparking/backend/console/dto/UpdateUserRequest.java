package com.dreamparking.backend.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.common.validation.FreeText;
import com.dreamparking.backend.common.validation.PersonName;
import com.dreamparking.backend.console.entity.enums.ConsoleRole;

/** Body to edit a console user. The email is the sign-in name and cannot be changed. */
public record UpdateUserRequest(@NotBlank @Size(max = 150) @PersonName String fullName,
		@NotBlank @Size(max = 120) @FreeText String jobTitle,
		@NotNull ConsoleRole role, @NotNull Boolean active) {
}
