package com.dreamparking.backend.customer.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dreamparking.backend.customer.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	Optional<Customer> findByDui(String dui);

	/** Whether the phone belongs to a customer with another DUI. */
	boolean existsByMobilePhoneAndDuiNot(String mobilePhone, String dui);

}
