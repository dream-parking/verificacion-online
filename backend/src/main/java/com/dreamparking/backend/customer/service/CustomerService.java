package com.dreamparking.backend.customer.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dreamparking.backend.common.exception.InvalidStateException;
import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.customer.dto.CustomerResponse;
import com.dreamparking.backend.customer.entity.Customer;
import com.dreamparking.backend.customer.repository.CustomerRepository;

@Service
public class CustomerService {

	static final String DUI_TAKEN = "El DUI ya se encuentra registrado";

	static final String PHONE_TAKEN = "El número de celular ya está en uso";

	private final CustomerRepository customers;

	public CustomerService(CustomerRepository customers) {
		this.customers = customers;
	}

	/**
	 * Customer with this DUI, created on first contact. A registered DUI cannot be taken over with other names or
	 * another phone (409); only the request that registered it ({@code current}) can correct its data. A phone
	 * that belongs to another DUI is rejected too (409).
	 */
	@Transactional
	public Customer register(String dui, String firstNames, String lastNames, String mobilePhone, Customer current) {
		Customer customer = customers.findByDui(dui).orElse(null);
		if (customer != null && !isSameCustomer(customer, current)
				&& !hasSameData(customer, firstNames, lastNames, mobilePhone)) {
			throw new InvalidStateException(DUI_TAKEN);
		}
		if (customers.existsByMobilePhoneAndDuiNot(mobilePhone, dui)) {
			throw new InvalidStateException(PHONE_TAKEN);
		}
		if (customer == null) {
			customer = new Customer();
		}
		customer.setDui(dui);
		customer.setFirstNames(firstNames);
		customer.setLastNames(lastNames);
		customer.setMobilePhone(mobilePhone);
		return customers.save(customer);
	}

	private static boolean isSameCustomer(Customer customer, Customer current) {
		return current != null && customer.getId().equals(current.getId());
	}

	private static boolean hasSameData(Customer customer, String firstNames, String lastNames, String mobilePhone) {
		return customer.getFirstNames().equalsIgnoreCase(firstNames)
				&& customer.getLastNames().equalsIgnoreCase(lastNames)
				&& customer.getMobilePhone().equals(mobilePhone);
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
