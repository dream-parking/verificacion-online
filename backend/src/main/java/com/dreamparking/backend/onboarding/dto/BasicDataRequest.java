package com.dreamparking.backend.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.dreamparking.backend.customer.entity.Customer;

/** Step "basic data": identity of the customer. Formats: DUI 00000000-0, mobile 7000-0000; names only
 * letters and spaces. */
public record BasicDataRequest(@NotBlank @Pattern(regexp = "[0-9]{8}-[0-9]") String dui,
		@NotBlank @Size(max = 100) @Pattern(regexp = BasicDataRequest.NAME, message = Customer.NAME_MESSAGE) String firstNames,
		@NotBlank @Size(max = 100) @Pattern(regexp = BasicDataRequest.NAME, message = Customer.NAME_MESSAGE) String lastNames,
		@NotBlank @Pattern(regexp = "[67][0-9]{3}-[0-9]{4}") String mobilePhone) {

	/** {@link Customer#NAME_PATTERN}, tolerating the surrounding blanks that the service trims. */
	static final String NAME = "\\s*" + Customer.NAME_PATTERN + "\\s*";

}
