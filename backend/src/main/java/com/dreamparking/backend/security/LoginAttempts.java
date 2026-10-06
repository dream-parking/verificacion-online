package com.dreamparking.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Blocks an email for a while after too many failed sign-ins in a row. In memory: it is per instance and is lost on
 * restart, which is enough for the single replica we run; revisit if the API scales out.
 */
@Component
public class LoginAttempts {

	static final int MAX_FAILURES = 5;

	static final Duration BLOCK = Duration.ofMinutes(15);

	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Clock clock;

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	public LoginAttempts(Clock clock) {
		this.clock = clock;
	}

	/** Seconds until the email can try again, or 0 when it is not blocked. */
	public long retryAfterSeconds(String email) {
		Entry entry = entries.get(email);
		if (entry == null || entry.blockedUntil == null) {
			return 0;
		}
		long left = Duration.between(clock.instant(), entry.blockedUntil).toSeconds();
		if (left <= 0) {
			entries.remove(email);
			return 0;
		}
		return left;
	}

	public void failed(String email) {
		if (entries.size() > CLEANUP_THRESHOLD) {
			Instant now = clock.instant();
			entries.values().removeIf(entry -> entry.blockedUntil != null && entry.blockedUntil.isBefore(now));
		}
		entries.compute(email, (key, entry) -> {
			Entry updated = entry == null ? new Entry() : entry;
			if (++updated.failures >= MAX_FAILURES) {
				updated.blockedUntil = clock.instant().plus(BLOCK);
				updated.failures = 0;
			}
			return updated;
		});
	}

	public void succeeded(String email) {
		entries.remove(email);
	}

	private static final class Entry {

		int failures;

		Instant blockedUntil;

	}

}
