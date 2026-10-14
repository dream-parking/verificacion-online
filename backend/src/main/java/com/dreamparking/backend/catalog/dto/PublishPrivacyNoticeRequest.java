package com.dreamparking.backend.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/** New version of the privacy notice. The text itself lives in the app; the hash proves which text was shown. */
public record PublishPrivacyNoticeRequest(
		@Schema(description = "Versión, única", example = "2026.2") @NotBlank @Pattern(regexp = "[0-9A-Za-z._-]{1,20}") String version,
		@Schema(description = "SHA-256 del texto que muestra la app, en hexadecimal", example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08") @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String textHash) {
}
