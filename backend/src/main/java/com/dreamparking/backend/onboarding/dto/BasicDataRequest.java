package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Step "basic data": identity of the customer. Formats: DUI 00000000-0, mobile 7000-0000. */
public record BasicDataRequest(@NotBlank @Pattern(regexp = "[0-9]{8}-[0-9]") String dui,
		@NotBlank @Size(max = 100) @Pattern(regexp = "\\p{L}+( \\p{L}+)*") String firstNames, @NotBlank @Size(max = 100) String lastNames,
		@NotBlank @Pattern(regexp = "[67][0-9]{3}-[0-9]{4}") String mobilePhone) {
}
