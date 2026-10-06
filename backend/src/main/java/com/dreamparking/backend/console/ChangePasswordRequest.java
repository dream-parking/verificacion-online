package com.dreamparking.backend.console;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
		@NotBlank @Size(max = 200) String newPassword) {
}
