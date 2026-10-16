package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.common.validation.Dui;
import com.dreamparking.backend.common.validation.MobilePhone;
import com.dreamparking.backend.common.validation.PersonName;

/**
 * Step "basic data": identity of the customer, the minimum set of four fields (VDI-22). Formats: DUI 00000000-0,
 * mobile 7000-0000; names letters joined by a space, apostrophe or hyphen. Why each field is asked and what the
 * NRP-23 requires to protect it: {@code docs/set-minimo-campos-nrp23.md} (VDI-85).
 */
public record BasicDataRequest(@NotBlank @Dui String dui, @NotBlank @Size(max = 100) @PersonName String firstNames,
		@NotBlank @Size(max = 100) @PersonName String lastNames, @NotBlank @MobilePhone String mobilePhone) {
}
