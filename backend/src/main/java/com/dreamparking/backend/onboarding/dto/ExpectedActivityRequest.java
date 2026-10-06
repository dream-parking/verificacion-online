package com.dreamparking.backend.onboarding.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body of step 3 (expected activity): how the customer will use the account and the monthly amount. */
public record ExpectedActivityRequest(@NotBlank String transactionTypeCode,
		@NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal monthlyAmountUsd) {
}
