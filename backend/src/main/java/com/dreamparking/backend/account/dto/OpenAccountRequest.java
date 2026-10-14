package com.dreamparking.backend.account.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.common.validation.SafeText;

/** Account opened in the core banking system for a submitted request; only its token and last four digits are kept. */
public record OpenAccountRequest(@NotNull UUID requestId, @NotBlank @Size(max = 64) @SafeText String numberToken,
		@NotBlank @Pattern(regexp = "[0-9]{4}") String lastFour) {
}
