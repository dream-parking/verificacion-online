package com.dreamparking.backend.customer.dto;

import java.time.Instant;
import java.util.UUID;

import com.dreamparking.backend.customer.entity.Customer;

public record CustomerResponse(UUID id, String dui, String firstNames, String lastNames, String mobilePhone,
		Instant createdAt) {

	public static CustomerResponse of(Customer customer) {
		return new CustomerResponse(customer.getId(), customer.getDui(), customer.getFirstNames(),
				customer.getLastNames(), customer.getMobilePhone(), customer.getCreatedAt());
	}

}
