package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.onboarding.entity.enums.TypingPace;

/** Session signals sent by the mobile app. The IP address and user agent are taken from the HTTP request. */
public record StartSessionRequest(@NotBlank @Size(max = 64) String deviceFingerprint,
		@Size(max = 80) String deviceModel, @Size(max = 30) String operatingSystem,
		@Size(max = 20) String appVersion, @Size(max = 120) String approximateLocation,
		@Pattern(regexp = "[A-Z]{2}") String countryIso, @Min(0) @Max(2000) Short typingSpeedCpm,
		TypingPace typingPace) {
}
