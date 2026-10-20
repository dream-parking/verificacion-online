package com.dreamparking.backend.customer.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.dreamparking.backend.customer.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	Optional<Customer> findByDui(String dui);

	Optional<Customer> findByNumber(String number);

	/** Whether the phone belongs to a customer with another DUI. */
	boolean existsByMobilePhoneAndDuiNot(String mobilePhone, String dui);

	/**
	 * Locks the customer's row until the end of the transaction and reads its number from the database, not from an
	 * entity that may be stale: another submission of the same customer can have assigned it since the customer was
	 * loaded. Empty while the customer has no number.
	 */
	@Query(value = "select number from customer where id = :id for update", nativeQuery = true)
	Optional<String> lockAndReadNumber(UUID id);

	/** Gives the customer their number, only if they still have none. Returns how many customers it reached (0 or 1). */
	@Modifying
	@Query(value = "update customer set number = :number where id = :id and number is null", nativeQuery = true)
	int assignNumber(UUID id, String number);

	/** Next customer number, such as CLI-0000001. A database sequence: it never repeats, not even under concurrency. */
	@Query(value = "select 'CLI-' || lpad(nextval('customer_number_seq')::text, 7, '0')", nativeQuery = true)
	String nextNumber();

}
