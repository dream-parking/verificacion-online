package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.common.validation.Dui;
import com.dreamparking.backend.common.validation.MobilePhone;
import com.dreamparking.backend.common.validation.PersonName;

/** Step "basic data": identity of the customer. Formats: DUI 00000000-0, mobile 7000-0000; names only
 * letters and spaces. */
public record BasicDataRequest(@NotBlank @Dui String dui, @NotBlank @Size(max = 100) @PersonName String firstNames,
		@NotBlank @Size(max = 100) @PersonName String lastNames, @NotBlank @MobilePhone String mobilePhone) {
}
