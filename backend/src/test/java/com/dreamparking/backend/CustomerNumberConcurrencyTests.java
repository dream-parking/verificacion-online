package com.dreamparking.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dreamparking.backend.customer.service.CustomerService;

/**
 * VDI-122: customer numbers do not repeat even when many requests are submitted at the same time. These tests commit
 * for real (each thread is its own transaction, as every request of the API is), so they are not transactional and
 * delete what they create.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class CustomerNumberConcurrencyTests {

	private static final int THREADS = 12;

	@Autowired
	CustomerService customerService;

	@Autowired
	JdbcTemplate jdbc;

	final List<UUID> created = new ArrayList<>();

	@AfterEach
	void deleteWhatWasCreated() {
		created.forEach(id -> jdbc.update("delete from customer where id = ?", id));
		created.clear();
	}

	@Test
	void customersThatSubmitAtTheSameTimeGetDifferentNumbers() throws Exception {
		List<UUID> customers = new ArrayList<>();
		for (int i = 0; i < THREADS; i++) {
			customers.add(newCustomer(i));
		}

		List<Callable<String>> tasks = new ArrayList<>();
		customers.forEach(id -> tasks.add(() -> customerService.assignNumber(id)));
		List<String> numbers = runTogether(tasks);

		assertThat(numbers).hasSize(THREADS).doesNotHaveDuplicates();
		assertThat(numbers).allMatch(number -> number.matches("CLI-[0-9]{7,}"));
		for (int i = 0; i < THREADS; i++) {
			assertThat(jdbc.queryForObject("select number from customer where id = ?", String.class, customers.get(i)))
				.isEqualTo(numbers.get(i));
		}
	}

	@Test
	void theSameCustomerSubmittingTwiceAtTheSameTimeKeepsOneNumber() throws Exception {
		UUID customer = newCustomer(100);

		Callable<String> assign = () -> customerService.assignNumber(customer);
		List<String> numbers = runTogether(Collections.nCopies(THREADS, assign));

		assertThat(new HashSet<>(numbers)).hasSize(1);
		assertThat(jdbc.queryForObject("select number from customer where id = ?", String.class, customer))
			.isEqualTo(numbers.get(0));
	}

	@Test
	void aCustomerKeepsTheNumberTheyAlreadyHave() {
		UUID customer = newCustomer(200);

		String first = customerService.assignNumber(customer);
		String again = customerService.assignNumber(customer);

		assertThat(again).isEqualTo(first);
	}

	private UUID newCustomer(int index) {
		UUID id = jdbc.queryForObject("""
				insert into customer (dui, first_names, last_names, mobile_phone)
				values (?, 'Concurrent', 'Customer', ?) returning id""", UUID.class, "9%07d-1".formatted(index),
				"7%03d-%04d".formatted(index, index));
		created.add(id);
		return id;
	}

	/** Runs the tasks at the same moment, each in its own thread, and returns what they answered. */
	private static List<String> runTogether(List<? extends Callable<String>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch go = new CountDownLatch(1);
			List<Future<String>> futures = new ArrayList<>();
			for (Callable<String> task : tasks) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return task.call();
				}));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<String> answers = new ArrayList<>();
			for (Future<String> future : futures) {
				answers.add(future.get(60, TimeUnit.SECONDS));
			}
			return answers;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
