package com.dreamparking.backend.rnpn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.dreamparking.backend.rnpn.client.RnpnClient;
import com.dreamparking.backend.rnpn.client.RnpnException;
import com.dreamparking.backend.rnpn.client.RnpnQuery;
import com.dreamparking.backend.rnpn.client.RnpnResult;
import com.dreamparking.backend.rnpn.entity.enums.RnpnField;
import com.dreamparking.backend.rnpn.entity.enums.RnpnOutcome;

/** VDI-111: the RNPN is retried when it does not answer, and a silent RNPN ends as UNAVAILABLE, not as an error. */
class RnpnServiceTests {

	private static final RnpnQuery QUERY = new RnpnQuery("01234567-8", "Ana Sofía", "Pérez López",
			LocalDate.of(1991, 3, 14));

	private final RnpnClient client = mock(RnpnClient.class);

	RnpnServiceTests() {
		when(client.provider()).thenReturn("simulated");
	}

	@Test
	void anAnswerAtTheFirstTryNeedsNoRetry() throws Exception {
		when(client.check(any())).thenReturn(RnpnResult.match());

		RnpnService.Answer answer = service(3, Duration.ZERO).check(QUERY);

		assertThat(answer.outcome()).isEqualTo(RnpnOutcome.MATCH);
		assertThat(answer.attempts()).isEqualTo(1);
		assertThat(answer.provider()).isEqualTo("simulated");
		assertThat(answer.failure()).isNull();
		verify(client, times(1)).check(QUERY);
	}

	@Test
	void dataThatDoesNotMatchIsAnAnswerAndIsNotRetried() throws Exception {
		when(client.check(any())).thenReturn(RnpnResult.noMatch(java.util.List.of(RnpnField.DUI)));

		RnpnService.Answer answer = service(3, Duration.ZERO).check(QUERY);

		assertThat(answer.outcome()).isEqualTo(RnpnOutcome.NO_MATCH);
		assertThat(answer.mismatchedFields()).containsExactly(RnpnField.DUI);
		assertThat(answer.attempts()).isEqualTo(1);
		verify(client, times(1)).check(QUERY);
	}

	@Test
	void anRnpnThatRecoversIsAskedAgainUntilItAnswers() throws Exception {
		when(client.check(any())).thenThrow(new RnpnException("timeout"))
			.thenThrow(new RnpnException("timeout"))
			.thenReturn(RnpnResult.match());

		RnpnService.Answer answer = service(3, Duration.ZERO).check(QUERY);

		assertThat(answer.outcome()).isEqualTo(RnpnOutcome.MATCH);
		assertThat(answer.attempts()).isEqualTo(3);
		assertThat(answer.failure()).isNull();
	}

	@Test
	void anRnpnThatNeverAnswersEndsUnavailableAfterAllTheTries() throws Exception {
		when(client.check(any())).thenThrow(new RnpnException("connection refused"));

		RnpnService.Answer answer = service(3, Duration.ZERO).check(QUERY);

		assertThat(answer.outcome()).isEqualTo(RnpnOutcome.UNAVAILABLE);
		assertThat(answer.attempts()).isEqualTo(3);
		assertThat(answer.failure()).isEqualTo("connection refused");
		assertThat(answer.mismatchedFields()).isEmpty();
		verify(client, times(3)).check(QUERY);
	}

	@Test
	void theWaitDoublesAfterEachFailedTryAndThereIsNoWaitAfterTheLastOne() throws Exception {
		when(client.check(any())).thenThrow(new RnpnException("timeout"));

		long start = System.nanoTime();
		service(3, Duration.ofMillis(40)).check(QUERY);
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		// 40 ms after the first try and 80 ms after the second; nothing after the third
		assertThat(elapsedMillis).isGreaterThanOrEqualTo(120);
		assertThat(elapsedMillis).isLessThan(120 + 1000);
	}

	@Test
	void anInterruptedWaitStopsTheCheckAndKeepsTheInterruption() throws Exception {
		when(client.check(any())).thenThrow(new RnpnException("timeout"));

		Thread.currentThread().interrupt();
		RnpnService.Answer answer;
		try {
			answer = service(3, Duration.ofMillis(50)).check(QUERY);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		}
		finally {
			Thread.interrupted();
		}

		assertThat(answer.outcome()).isEqualTo(RnpnOutcome.UNAVAILABLE);
		assertThat(answer.attempts()).isEqualTo(1);
		verify(client, times(1)).check(QUERY);
	}

	@Test
	void atLeastOneTryIsRequired() {
		assertThatThrownBy(() -> service(0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
	}

	private RnpnService service(int attempts, Duration delay) {
		return new RnpnService(client, attempts, delay);
	}

}
