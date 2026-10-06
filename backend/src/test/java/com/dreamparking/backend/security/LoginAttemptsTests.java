package com.dreamparking.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class LoginAttemptsTests {

	private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-06T12:00:00Z"));

	private final Clock clock = new Clock() {
		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now.get();
		}
	};

	private final LoginAttempts attempts = new LoginAttempts(clock);

	@Test
	void blocksAfterFiveFailuresAndUnblocksAfterTheWindow() {
		for (int i = 0; i < 4; i++) {
			attempts.failed("a@x");
		}
		assertThat(attempts.retryAfterSeconds("a@x")).isZero();

		attempts.failed("a@x");
		assertThat(attempts.retryAfterSeconds("a@x")).isEqualTo(Duration.ofMinutes(15).toSeconds());
		assertThat(attempts.retryAfterSeconds("otro@x")).isZero();

		now.set(now.get().plus(Duration.ofMinutes(16)));
		assertThat(attempts.retryAfterSeconds("a@x")).isZero();
	}

	@Test
	void aSuccessClearsTheCount() {
		for (int i = 0; i < 4; i++) {
			attempts.failed("a@x");
		}
		attempts.succeeded("a@x");
		attempts.failed("a@x");
		assertThat(attempts.retryAfterSeconds("a@x")).isZero();
	}

}
