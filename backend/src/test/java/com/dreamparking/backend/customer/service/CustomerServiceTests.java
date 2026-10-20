package com.dreamparking.backend.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.common.exception.NotFoundException;
import com.dreamparking.backend.customer.repository.CustomerRepository;

/** VDI-122: how a customer is given their number, without a database. */
class CustomerServiceTests {

	private final CustomerRepository customers = mock(CustomerRepository.class);

	private final CustomerService service = new CustomerService(customers);

	private final UUID id = UUID.randomUUID();

	@Test
	void aCustomerWithoutANumberGetsTheNextOneFromTheSequence() {
		when(customers.lockAndReadNumber(id)).thenReturn(Optional.empty());
		when(customers.nextNumber()).thenReturn("CLI-0000042");
		when(customers.assignNumber(id, "CLI-0000042")).thenReturn(1);

		assertThat(service.assignNumber(id)).isEqualTo("CLI-0000042");
	}

	@Test
	void aCustomerThatHasANumberKeepsItAndTheSequenceIsNotUsed() {
		when(customers.lockAndReadNumber(id)).thenReturn(Optional.of("CLI-0000007"));

		assertThat(service.assignNumber(id)).isEqualTo("CLI-0000007");

		verify(customers, never()).nextNumber();
		verify(customers, never()).assignNumber(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void aCustomerThatDoesNotExistIsNotFound() {
		when(customers.lockAndReadNumber(id)).thenReturn(Optional.empty());
		when(customers.nextNumber()).thenReturn("CLI-0000043");
		when(customers.assignNumber(id, "CLI-0000043")).thenReturn(0);

		assertThatThrownBy(() -> service.assignNumber(id)).isInstanceOf(NotFoundException.class);
	}

}
