package com.dreamparking.backend.console.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Access token to send as {@code Authorization: Bearer <accessToken>} and the user it belongs to. */
public record LoginResponse(String accessToken, @Schema(example = "Bearer") String tokenType,
		@Schema(description = "Segundos de vigencia del token", example = "28800") long expiresIn,
		ConsoleUserResponse user) {
}
