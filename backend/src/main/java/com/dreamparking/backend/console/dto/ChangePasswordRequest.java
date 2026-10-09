package com.dreamparking.backend.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.common.validation.Password;

public record ChangePasswordRequest(@NotBlank @Size(max = 200) @Password String currentPassword,
		@NotBlank @Size(max = 200) @Password String newPassword) {
}
