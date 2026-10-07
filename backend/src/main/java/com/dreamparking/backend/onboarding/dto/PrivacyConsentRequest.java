package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Step 1: the customer must accept the privacy notice, including the capture of session signals. */
public record PrivacyConsentRequest(@NotNull @AssertTrue Boolean signalsAccepted) {
}
