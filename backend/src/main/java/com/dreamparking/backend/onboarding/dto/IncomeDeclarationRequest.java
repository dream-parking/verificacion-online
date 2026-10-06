package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of step 2 (income). {@code sourceDetail} is required when the source is {@code OTRO}. */
public record IncomeDeclarationRequest(@NotBlank String sourceCode, @Size(max = 150) String sourceDetail,
		@NotBlank String rangeCode) {
}
