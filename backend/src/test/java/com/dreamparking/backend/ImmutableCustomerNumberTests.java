package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * VDI-122: a customer number is unique and permanent. Not even SQL can change it, remove it or repeat it, and the
 * customers that had already submitted a request when the number was introduced got theirs.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ImmutableCustomerNumberTests {

	@Autowired
	JdbcTemplate jdbc;

	String customerId;

	@BeforeEach
	void numberedCustomer() {
		customerId = jdbc.queryForObject("""
				insert into customer (dui, first_names, last_names, mobile_phone, number)
				values ('09123456-7', 'Ana', 'Pérez', '7123-4100', 'CLI-8000001') returning id::text""", String.class);
	}

	@Test
	void aCustomerWithoutANumberCanBeGivenOne() {
		String other = jdbc.queryForObject("""
				insert into customer (dui, first_names, last_names, mobile_phone)
				values ('09123457-5', 'Luis', 'Ramos', '7123-4101') returning id::text""", String.class);

		int updated = jdbc.update("update customer set number = 'CLI-8000002' where id = ?::uuid", other);

		assertThat(updated).isEqualTo(1);
	}

	@Test
	void theNumberCannotBeChanged() {
		assertThatThrownBy(() -> jdbc.update("update customer set number = 'CLI-8000009' where id = ?::uuid", customerId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be changed or removed");
	}

	@Test
	void theNumberCannotBeRemoved() {
		assertThatThrownBy(() -> jdbc.update("update customer set number = null where id = ?::uuid", customerId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("cannot be changed or removed");
	}

	@Test
	void otherDataOfTheCustomerCanStillBeCorrected() {
		int updated = jdbc.update("update customer set first_names = 'Ana María' where id = ?::uuid", customerId);

		assertThat(updated).isEqualTo(1);
		assertThat(jdbc.queryForObject("select number from customer where id = ?::uuid", String.class, customerId))
			.isEqualTo("CLI-8000001");
	}

	@Test
	void twoCustomersCannotShareANumber() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into customer (dui, first_names, last_names, mobile_phone, number)
				values ('09123458-3', 'Luis', 'Ramos', '7123-4102', 'CLI-8000001')"""))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uq_customer_number");
	}

	@Test
	void theNumberHasTheCustomerFormat() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into customer (dui, first_names, last_names, mobile_phone, number)
				values ('09123458-3', 'Luis', 'Ramos', '7123-4102', 'CLI-12')"""))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ck_customer_number");
	}

	@Test
	void theSequenceGivesGrowingNumbersInTheCustomerFormat() {
		String first = jdbc.queryForObject("select 'CLI-' || lpad(nextval('customer_number_seq')::text, 7, '0')",
				String.class);
		String second = jdbc.queryForObject("select 'CLI-' || lpad(nextval('customer_number_seq')::text, 7, '0')",
				String.class);

		assertThat(first).matches("CLI-[0-9]{7,}");
		assertThat(Long.parseLong(second.substring(4))).isGreaterThan(Long.parseLong(first.substring(4)));
	}

	@Test
	void everyCustomerThatAlreadySubmittedARequestHasANumber() {
		assertThat(jdbc.queryForObject("""
				select count(*) from customer c
				 where c.number is null
				   and exists (select 1 from onboarding_request r where r.customer_id = c.id and r.submitted_at is not null)""",
				Integer.class))
			.isZero();
	}

}
