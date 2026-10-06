package com.dreamparking.backend.customer.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.customer.dto.CustomerResponse;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.customer.repository.CustomerRepository;

@Service
public class CustomerService {

	private final CustomerRepository customers;

	public CustomerService(CustomerRepository customers) {
		this.customers = customers;
	}

	/**
	 * Customer with this DUI, created on first contact. A returning customer keeps their record and gets the
	 * names and phone they just declared.
	 */
	@Transactional
	public Customer register(String dui, String firstNames, String lastNames, String mobilePhone) {
		Customer customer = customers.findByDui(dui).orElseGet(Customer::new);
		customer.setDui(dui);
		customer.setFirstNames(firstNames);
		customer.setLastNames(lastNames);
		customer.setMobilePhone(mobilePhone);
		return customers.save(customer);
	}

	@Transactional(readOnly = true)
	public CustomerResponse get(UUID customerId) {
		return CustomerResponse.of(customers.findById(customerId)
			.orElseThrow(() -> new NotFoundException("Customer not found: " + customerId)));
	}

	@Transactional(readOnly = true)
	public CustomerResponse getByDui(String dui) {
		return CustomerResponse.of(customers.findByDui(dui)
			.orElseThrow(() -> new NotFoundException("Customer not found for DUI " + dui)));
	}

}
