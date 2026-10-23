package com.dreamparking.backend.customer.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.customer.entity.Customer;

/** {@code number} is the customer number (CLI-0000001), null until the customer submits the first request. */
public record CustomerResponse(UUID id, String number, String dui, String firstNames, String lastNames,
		String mobilePhone, Instant createdAt) {

	public static CustomerResponse of(Customer customer) {
		return new CustomerResponse(customer.getId(), customer.getNumber(), customer.getDui(), customer.getFirstNames(),
				customer.getLastNames(), customer.getMobilePhone(), customer.getCreatedAt());
	}

}
