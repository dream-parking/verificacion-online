package com.dreamparking.backend.console;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body to edit a console user. The email is the sign-in name and cannot be changed. */
public record UpdateUserRequest(@NotBlank @Size(max = 150) String fullName, @NotBlank @Size(max = 120) String jobTitle,
		@NotNull ConsoleRole role, @NotNull Boolean active) {
}
